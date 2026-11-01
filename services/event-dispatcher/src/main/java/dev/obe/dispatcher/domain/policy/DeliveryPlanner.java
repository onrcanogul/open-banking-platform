package dev.obe.dispatcher.domain.policy;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.listener.EventNotification;
import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Decides which events go to which YOS and how they are split into calls. */
@Component
public class DeliveryPlanner {

    /**
     * @param events        events in the order they were read
     * @param subscriptions subscriptions by YOS code, without YOSes that have none
     */
    public List<NotificationEvent> subscribed(List<NotificationEvent> events,
            Map<String, SubscriptionView> subscriptions) {
        return events.stream()
                .filter(event -> isSubscribed(event, subscriptions.get(event.yosCode())))
                .toList();
    }

    /** Groups events by YOS in the given order and splits each group into calls the standard allows. */
    public List<DeliveryBatch> batches(List<NotificationEvent> events) {
        Map<String, List<NotificationEvent>> byYos = new LinkedHashMap<>();
        for (NotificationEvent event : events) {
            byYos.computeIfAbsent(event.yosCode(), code -> new ArrayList<>()).add(event);
        }
        List<DeliveryBatch> batches = new ArrayList<>();
        byYos.forEach((yosCode, yosEvents) -> batches.addAll(split(yosCode, yosEvents)));
        return batches;
    }

    private boolean isSubscribed(NotificationEvent event, SubscriptionView subscription) {
        return subscription != null
                && subscription.types().contains(new SubscribedType(event.eventType(), event.resourceType()));
    }

    private List<DeliveryBatch> split(String yosCode, List<NotificationEvent> events) {
        List<DeliveryBatch> batches = new ArrayList<>();
        for (int start = 0; start < events.size(); start += EventNotification.MAX_EVENTS) {
            int end = Math.min(start + EventNotification.MAX_EVENTS, events.size());
            batches.add(new DeliveryBatch(yosCode, List.copyOf(events.subList(start, end))));
        }
        return batches;
    }
}
