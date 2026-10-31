package dev.obe.dispatcher.domain.model;

public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    /** Retries ran out, so the YOS has to collect it through the undelivered events API. */
    UNDELIVERED,
    /** A newer undelivered event of the same resource replaced this one. */
    SUPERSEDED
}
