package dev.obe.disbursement.application.port.out;

/** The request went out and no clear answer came back, so the work may or may not have been done. */
public class CoreOutcomeUnknownException extends RuntimeException {

    public CoreOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
