package dev.obe.mockcore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Makes a step misbehave on demand, to try out what a caller does when the core lets it down. */
@Component
public class FaultInjector {

    public enum Mode {
        /** Refuses with a business error. The work is not done. */
        REFUSE,
        /** Never answers and does not do the work. */
        HANG,
        /** Does the work and then never answers. The caller cannot tell this apart from HANG. */
        DO_THEN_HANG
    }

    private final Map<String, Mode> faults = new ConcurrentHashMap<>();
    private final Duration hangTime;

    public FaultInjector(@Value("${obe.fault-hang-time}") Duration hangTime) {
        this.hangTime = hangTime;
    }

    public void set(String step, Mode mode) {
        faults.put(step, mode);
    }

    public void clear() {
        faults.clear();
    }

    public Mode modeOf(String step) {
        return faults.get(step);
    }

    /** Waits longer than the caller's read timeout, which then gives up without an answer. */
    public void hang() {
        try {
            Thread.sleep(hangTime.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
