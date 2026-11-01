package dev.obe.disbursement.infrastructure.core;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreRefusedException;
import dev.obe.disbursement.infrastructure.core.CoreBankingApi.LimitRequest;
import dev.obe.disbursement.infrastructure.core.CoreBankingApi.OperationRequest;
import feign.FeignException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
class FeignCoreBankingClient implements CoreBankingClient {

    private final CoreBankingApi api;

    FeignCoreBankingClient(CoreBankingApi api) {
        this.api = api;
    }

    @Override
    public void reserveLimit(String operationId, String customerNo, BigDecimal amount) {
        call(() -> api.reserveLimit(new LimitRequest(operationId, customerNo, amount)));
    }

    @Override
    public void postAccounting(String operationId) {
        call(() -> api.postAccounting(new OperationRequest(operationId)));
    }

    @Override
    public void transfer(String operationId) {
        call(() -> api.transfer(new OperationRequest(operationId)));
    }

    private void call(Runnable request) {
        try {
            request.run();
        } catch (FeignException.UnprocessableEntity ex) {
            throw new CoreRefusedException(ex.getMessage());
        }
    }
}
