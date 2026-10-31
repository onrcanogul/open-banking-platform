package dev.obe.subscription.application.result;

import dev.obe.subscription.application.command.Participant;
import dev.obe.subscription.domain.model.EventSubscription;
import dev.obe.subscription.domain.model.SubscriptionType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record SubscriptionResult(
        String subscriptionNo,
        Instant createdAt,
        Instant updatedAt,
        Participant participant,
        List<SubscriptionType> types) {

    public static SubscriptionResult from(EventSubscription subscription, String hhsCode) {
        List<SubscriptionType> sortedTypes = subscription.getTypes().stream()
                .sorted(Comparator.comparing(SubscriptionType::eventType)
                        .thenComparing(SubscriptionType::resourceType))
                .toList();
        return new SubscriptionResult(subscription.getSubscriptionNo(), subscription.getCreatedAt(),
                subscription.getUpdatedAt(), new Participant(hhsCode, subscription.getYosCode()), sortedTypes);
    }
}
