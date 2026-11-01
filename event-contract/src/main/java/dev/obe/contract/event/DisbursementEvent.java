package dev.obe.contract.event;

import java.time.Instant;

/**
 * Tells a partner how a disbursement ended.
 *
 * @param eventNo unique per event, so a partner can drop a repeated notification
 * @param status  the final status, TAMAMLANDI or BASARISIZ
 */
public record DisbursementEvent(
        String eventNo,
        String disbursementNo,
        String partnerCode,
        String status,
        String failureReason,
        Instant occurredAt,
        String correlationId) {
}
