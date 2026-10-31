package dev.obe.subscription.domain.policy;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Time range of an undelivered events query. The standard offers events from the start of the
 * previous day up to the query time, whatever wider range the YOS asks for.
 */
public record UndeliveredWindow(Instant from, Instant to) {

    private static final ZoneId BANK_ZONE = ZoneId.of("Europe/Istanbul");

    public static UndeliveredWindow of(Instant now, Instant requestedFrom, Instant requestedTo) {
        Instant earliest = now.atZone(BANK_ZONE).toLocalDate().minusDays(1).atStartOfDay(BANK_ZONE).toInstant();
        Instant from = requestedFrom == null || requestedFrom.isBefore(earliest) ? earliest : requestedFrom;
        Instant to = requestedTo == null || requestedTo.isAfter(now) ? now : requestedTo;
        return new UndeliveredWindow(from, to);
    }
}
