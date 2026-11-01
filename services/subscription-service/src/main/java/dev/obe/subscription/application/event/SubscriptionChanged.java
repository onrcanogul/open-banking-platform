package dev.obe.subscription.application.event;

/** Published inside the transaction that created, changed or removed a subscription. */
public record SubscriptionChanged(String yosCode) {
}
