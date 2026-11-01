package dev.obe.dispatcher.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import dev.obe.dispatcher.domain.policy.DeliveryPlanner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Turns consumed events into delivery records. Sending happens later, so a YOS cannot hold back Kafka. */
@Service
public class EventIntakeService {

    private final SubscriptionClient subscriptionClient;
    private final DeliveryPlanner planner;
    private final DeliveryStore store;

    public EventIntakeService(SubscriptionClient subscriptionClient, DeliveryPlanner planner, DeliveryStore store) {
        this.subscriptionClient = subscriptionClient;
        this.planner = planner;
        this.store = store;
    }

    public void accept(List<NotificationEvent> events) {
        store.saveNew(planner.subscribed(events, subscriptionsOf(events)));
    }

    // One lookup per YOS, however many events it has in this read
    private Map<String, SubscriptionView> subscriptionsOf(List<NotificationEvent> events) {
        Map<String, SubscriptionView> subscriptions = new HashMap<>();
        events.stream()
                .map(NotificationEvent::yosCode)
                .distinct()
                .forEach(yosCode -> subscriptionClient.find(yosCode)
                        .ifPresent(subscription -> subscriptions.put(yosCode, subscription)));
        return subscriptions;
    }
}
