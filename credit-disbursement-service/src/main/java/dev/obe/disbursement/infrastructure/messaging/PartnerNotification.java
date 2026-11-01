package dev.obe.disbursement.infrastructure.messaging;

import dev.obe.contract.event.DisbursementEvent;
import java.time.Instant;

/** What a partner receives. A separate shape, so internal fields cannot leave the bank by accident. */
record PartnerNotification(
        String eventNo,
        String disbursementNo,
        String status,
        String failureReason,
        Instant occurredAt) {

    static PartnerNotification from(DisbursementEvent event) {
        return new PartnerNotification(event.eventNo(), event.disbursementNo(), event.status(), event.failureReason(),
                event.occurredAt());
    }
}
