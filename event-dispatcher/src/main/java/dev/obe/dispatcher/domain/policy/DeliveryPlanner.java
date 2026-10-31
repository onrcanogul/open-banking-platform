package dev.obe.dispatcher.domain.policy;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.listener.EventNotificationRequest;
import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
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
    public List<DeliveryBatch> plan(List<NotificationEvent> events, Map<String, SubscriptionView> subscriptions) {
        // TODO(exercise): keep an event only if the subscription of its YOS contains its
        // SubscribedType. Group kept events by YOS in read order, then split each group into
        // DeliveryBatch values of at most EventNotificationRequest.MAX_EVENTS events.
        throw new UnsupportedOperationException("Delivery planning is not implemented yet");
    }
}
