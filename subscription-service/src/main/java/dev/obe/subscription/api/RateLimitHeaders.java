package dev.obe.subscription.api;

/** Response headers the standard requires on rate limited services. */
public final class RateLimitHeaders {

    public static final String LIMIT = "X-RateLimit-Limit";
    public static final String REMAINING = "X-RateLimit-Remaining";
    public static final String RESET = "X-RateLimit-Reset";

    /** One undelivered events query per period, so a served query leaves nothing. */
    public static final String UNDELIVERED_QUERY_LIMIT = "1";
    public static final String UNDELIVERED_QUERY_REMAINING = "0";

    private RateLimitHeaders() {
    }
}
