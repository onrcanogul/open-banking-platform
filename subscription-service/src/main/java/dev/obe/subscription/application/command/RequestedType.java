package dev.obe.subscription.application.command;

/** Plain strings so unknown values reach the resolver and get the code the standard requires. */
public record RequestedType(String eventType, String resourceType) {
}
