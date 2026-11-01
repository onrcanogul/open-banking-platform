package dev.obe.disbursement.application.port.out;

import java.math.BigDecimal;

/**
 * Every call carries the disbursement number as its operation id, so the core does the work once
 * however many times it is called. There are three ways a call can go wrong, and they mean different
 * things: {@link CoreRefusedException} is a no, {@link CoreUnreachableException} means the request
 * never arrived, and {@link CoreOutcomeUnknownException} means it may or may not have taken effect.
 */
public interface CoreBankingClient {

    enum Step {
        LIMIT("limit"),
        ACCOUNTING("accounting"),
        TRANSFER("transfer");

        private final String path;

        Step(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    void reserveLimit(String operationId, String customerNo, BigDecimal amount);

    void postAccounting(String operationId);

    void transfer(String operationId);

    void reverseAccounting(String operationId);

    void releaseLimit(String operationId);

    /** Asks whether a step took effect, after a call whose outcome is unknown. */
    boolean isCompleted(Step step, String operationId);
}
