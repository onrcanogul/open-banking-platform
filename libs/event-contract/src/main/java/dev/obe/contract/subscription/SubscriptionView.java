package dev.obe.contract.subscription;

import java.util.List;

/** What a YOS subscribed to, as other services see it. */
public record SubscriptionView(String yosCode, List<SubscribedType> types) {
}
