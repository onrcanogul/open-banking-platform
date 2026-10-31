package dev.obe.subscription.application.command;

import java.util.List;

/**
 * @param caller                 participant identified by the request headers
 * @param declared               participant stated in the request body
 * @param subscriptionNo         subscription number from the path
 * @param declaredSubscriptionNo subscription number stated in the request body
 */
public record UpdateSubscriptionCommand(
        Participant caller,
        Participant declared,
        String subscriptionNo,
        String declaredSubscriptionNo,
        List<RequestedType> types) {
}
