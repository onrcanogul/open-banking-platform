package dev.obe.disbursement.application.result;

import java.time.Instant;

/** A case reconciliation reported for a person to review. */
public record FindingResult(
        String disbursementNo,
        String type,
        String ourStatus,
        boolean coreTransfer,
        String correlationId,
        Instant foundAt) {
}
