package dev.obe.dispatcher.infrastructure.batch;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import dev.obe.dispatcher.domain.policy.DeliveryPlanner;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import java.util.List;
import java.util.Map;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps only events that should have a delivery record and do not. Returning null drops an item.
 * The check uses the subscription of today, so one that changed since the event can cause a finding
 * to be raised or missed. Findings are for a person to review, not proof of a loss.
 */
@Component
class MissingDeliveryProcessor implements ItemProcessor<NotificationEvent, NotificationEvent> {

    private static final String COUNT_FINDINGS = "SELECT COUNT(*) FROM reconciliation_finding WHERE event_no = ?";

    private final EventDeliveryRepository repository;
    private final SubscriptionClient subscriptionClient;
    private final DeliveryPlanner planner;
    private final JdbcTemplate jdbc;

    MissingDeliveryProcessor(EventDeliveryRepository repository, SubscriptionClient subscriptionClient,
            DeliveryPlanner planner, JdbcTemplate jdbc) {
        this.repository = repository;
        this.subscriptionClient = subscriptionClient;
        this.planner = planner;
        this.jdbc = jdbc;
    }

    @Override
    public NotificationEvent process(NotificationEvent event) {
        if (repository.existsById(event.eventNo()) || alreadyReported(event)) {
            return null;
        }
        Map<String, SubscriptionView> subscription = subscriptionClient.find(event.yosCode())
                .map(view -> Map.of(event.yosCode(), view))
                .orElse(Map.of());
        return planner.subscribed(List.of(event), subscription).isEmpty() ? null : event;
    }

    // The window of a daily run overlaps the previous one, and a loss should be counted once
    private boolean alreadyReported(NotificationEvent event) {
        Integer findings = jdbc.queryForObject(COUNT_FINDINGS, Integer.class, event.eventNo());
        return findings != null && findings > 0;
    }
}
