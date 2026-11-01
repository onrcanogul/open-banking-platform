package dev.obe.mockcore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/**
 * Stands in for core banking, in memory. An operation id is remembered with its result, so a repeated
 * call does nothing and gets the first answer. That is what makes it safe for a caller to retry.
 */
@Component
public class CoreLedger {

    private final Map<String, BigDecimal> availableLimits;
    private final Map<String, Boolean> completedOperations = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final List<String> history = new CopyOnWriteArrayList<>();

    private record Reservation(String customerNo, BigDecimal amount) {
    }

    public CoreLedger(CoreProperties properties) {
        this.availableLimits = new ConcurrentHashMap<>(properties.customerLimits());
    }

    /** Returns whether the limit could be reserved. */
    public synchronized boolean reserveLimit(String operationId, String customerNo, BigDecimal amount) {
        return once("limit:" + operationId, () -> {
            BigDecimal available = availableLimits.getOrDefault(customerNo, BigDecimal.ZERO);
            if (available.compareTo(amount) < 0) {
                return false;
            }
            availableLimits.put(customerNo, available.subtract(amount));
            reservations.put(operationId, new Reservation(customerNo, amount));
            return true;
        });
    }

    /** Gives a reserved amount back. With no reservation there is nothing to do, and that is fine. */
    public synchronized void releaseLimit(String operationId) {
        once("limit-release:" + operationId, () -> {
            Reservation reservation = reservations.get(operationId);
            if (reservation != null) {
                availableLimits.merge(reservation.customerNo(), reservation.amount(), BigDecimal::add);
            }
            return true;
        });
    }

    public synchronized void postAccounting(String operationId) {
        once("accounting:" + operationId, () -> true);
    }

    /** Adds an opposite entry. The original stays in the ledger, since entries are never deleted. */
    public synchronized void reverseAccounting(String operationId) {
        once("accounting-reversal:" + operationId, () -> true);
    }

    public synchronized void transfer(String operationId) {
        once("transfer:" + operationId, () -> true);
    }

    /** Answers whether an operation really took effect, for a caller that never got a reply. */
    public boolean isCompleted(String step, String operationId) {
        return Boolean.TRUE.equals(completedOperations.get(step + ":" + operationId));
    }

    public BigDecimal availableLimit(String customerNo) {
        return availableLimits.getOrDefault(customerNo, BigDecimal.ZERO);
    }

    public List<String> history() {
        return List.copyOf(history);
    }

    private boolean once(String operation, BooleanSupplier action) {
        return completedOperations.computeIfAbsent(operation, key -> {
            boolean result = action.getAsBoolean();
            history.add(operation + "=" + result);
            return result;
        });
    }
}
