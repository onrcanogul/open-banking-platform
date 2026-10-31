package dev.obe.dispatcher.domain.policy;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import java.time.Duration;

/**
 * How a failed delivery is retried, per the "Retry Policy" column of OHVPS v2.0.1 section 12.
 *
 * @param retries number of attempts after the first one
 * @param window  time between the first and the last attempt
 */
public record RetryPolicy(int retries, Duration window) {

    private static final RetryPolicy NONE = new RetryPolicy(0, Duration.ZERO);
    private static final RetryPolicy THIRTY_MINUTES = new RetryPolicy(3, Duration.ofMinutes(30));
    private static final RetryPolicy ONE_MINUTE = new RetryPolicy(3, Duration.ofMinutes(1));

    public static RetryPolicy of(EventType eventType, ResourceType resourceType) {
        if (resourceType == ResourceType.BAKIYE) {
            return NONE;
        }
        boolean separateAuth = eventType == EventType.AYRIK_GKD_BASARILI || eventType == EventType.AYRIK_GKD_BASARISIZ;
        if (separateAuth || resourceType == ResourceType.KART_BILGILERI) {
            return ONE_MINUTE;
        }
        return THIRTY_MINUTES;
    }

    /** Whether another attempt is allowed after this many failures. */
    public boolean allowsRetryAfter(int failedAttempts) {
        return failedAttempts <= retries;
    }

    /**
     * Wait before the next attempt. Each wait doubles the previous one, and all waits add up to the
     * window, so three retries in thirty minutes wait for 1/7, 2/7 and 4/7 of it.
     */
    public Duration delayAfter(int failedAttempts) {
        long parts = (1L << retries) - 1;
        return window.multipliedBy(1L << (failedAttempts - 1)).dividedBy(parts);
    }
}
