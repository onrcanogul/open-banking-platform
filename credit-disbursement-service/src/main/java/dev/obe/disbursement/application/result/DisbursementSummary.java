package dev.obe.disbursement.application.result;

import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** A row of the staff list. Customer and account numbers stay out: following a case does not need them. */
public record DisbursementSummary(
        String disbursementNo,
        String partnerCode,
        DisbursementStatus status,
        BigDecimal amount,
        String currency,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static DisbursementSummary from(Disbursement disbursement) {
        return new DisbursementSummary(disbursement.getDisbursementNo(), disbursement.getPartnerCode(),
                disbursement.getStatus(), disbursement.getAmount(), disbursement.getCurrency(),
                disbursement.getFailureReason(), disbursement.getCreatedAt(), disbursement.getUpdatedAt());
    }
}
