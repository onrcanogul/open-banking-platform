package dev.obe.disbursement.application.port.out;

/** No connection was made, so the request cannot have taken effect and a retry is safe. */
public class CoreUnreachableException extends RuntimeException {

    public CoreUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
