package dev.obe.disbursement.application.port.out;

/** The core answered and said no. This differs from getting no answer, where the outcome is unknown. */
public class CoreRefusedException extends RuntimeException {

    public CoreRefusedException(String reason) {
        super(reason);
    }
}
