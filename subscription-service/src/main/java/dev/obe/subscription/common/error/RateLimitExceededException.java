package dev.obe.subscription.common.error;

public class RateLimitExceededException extends OhvpsException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(OhvpsError.EXCEEDED_RATE);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
