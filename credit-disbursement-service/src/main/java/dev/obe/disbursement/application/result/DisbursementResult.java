package dev.obe.disbursement.application.result;

import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import java.math.BigDecimal;

public record DisbursementResult(
        String disbursementNo,
        DisbursementStatus status,
        BigDecimal amount,
        String currency,
        String failureReason) {

    public static DisbursementResult from(Disbursement disbursement) {
        return new DisbursementResult(disbursement.getDisbursementNo(), disbursement.getStatus(),
                disbursement.getAmount(), disbursement.getCurrency(), disbursement.getFailureReason());
    }
}
