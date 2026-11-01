package dev.obe.disbursement.application.port.out;

import java.math.BigDecimal;

/**
 * Every call carries the disbursement number as its operation id, so the core does the work once
 * however many times it is called. A refusal throws {@link CoreRefusedException}.
 */
public interface CoreBankingClient {

    void reserveLimit(String operationId, String customerNo, BigDecimal amount);

    void postAccounting(String operationId);

    void transfer(String operationId);
}
