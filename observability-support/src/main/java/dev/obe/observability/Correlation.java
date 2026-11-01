package dev.obe.observability;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * The id that ties together everything done for one request. It lives in the logging context, which
 * belongs to one thread, so work on another thread must set it again with {@link #run}.
 */
public final class Correlation {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "correlationId";

    private Correlation() {
    }

    /** Returns the id of the current thread, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Runs the action with the given id in the logs, then restores what was there. */
    public static void run(String correlationId, Runnable action) {
        String previous = current();
        set(correlationId);
        try {
            action.run();
        } finally {
            set(previous);
        }
    }

    private static void set(String correlationId) {
        if (correlationId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }
    }
}
