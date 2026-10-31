package dev.obe.subscription.application.command;

import java.util.List;

/**
 * @param caller   participant identified by the request headers
 * @param declared participant stated in the request body
 */
public record CreateSubscriptionCommand(Participant caller, Participant declared, List<RequestedType> types) {
}
