package dev.obe.contract.subscription;

/** Redis keys shared by the service that clears subscriptions and the service that caches them. */
public final class SubscriptionCacheKeys {

    private static final String PREFIX = "obe:subscription:";

    private SubscriptionCacheKeys() {
    }

    public static String of(String yosCode) {
        return PREFIX + yosCode;
    }
}
