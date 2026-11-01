package dev.obe.subscription.common.error;

public class OhvpsException extends RuntimeException {

    private final OhvpsError error;

    public OhvpsException(OhvpsError error) {
        this(error, error.message());
    }

    public OhvpsException(OhvpsError error, String detail) {
        super(detail);
        this.error = error;
    }

    public OhvpsError error() {
        return error;
    }
}
