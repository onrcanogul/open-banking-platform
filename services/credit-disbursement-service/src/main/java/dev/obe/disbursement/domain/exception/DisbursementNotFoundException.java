package dev.obe.disbursement.domain.exception;

public class DisbursementNotFoundException extends RuntimeException {

    public DisbursementNotFoundException(String disbursementNo) {
        super("Disbursement not found: " + disbursementNo);
    }
}
