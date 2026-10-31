package dev.obe.dispatcher.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.EventListenerClient;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import dev.obe.dispatcher.domain.policy.DeliveryPlanner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventDispatchService {

    private final SubscriptionClient subscriptionClient;
    private final EventListenerClient listenerClient;
    private final DeliveryPlanner planner;

    public EventDispatchService(SubscriptionClient subscriptionClient, EventListenerClient listenerClient,
            DeliveryPlanner planner) {
        this.subscriptionClient = subscriptionClient;
        this.listenerClient = listenerClient;
        this.planner = planner;
    }

    public void dispatch(List<NotificationEvent> events) {
        planner.plan(events, subscriptionsOf(events)).forEach(listenerClient::deliver);
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
