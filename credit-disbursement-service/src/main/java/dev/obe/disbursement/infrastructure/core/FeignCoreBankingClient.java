package dev.obe.disbursement.infrastructure.core;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreOutcomeUnknownException;
import dev.obe.disbursement.application.port.out.CoreRefusedException;
import dev.obe.disbursement.application.port.out.CoreUnreachableException;
import dev.obe.disbursement.infrastructure.core.CoreBankingApi.LimitRequest;
import dev.obe.disbursement.infrastructure.core.CoreBankingApi.OperationRequest;
import feign.FeignException;
import feign.RetryableException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class FeignCoreBankingClient implements CoreBankingClient {

    private final CoreBankingApi api;

    FeignCoreBankingClient(CoreBankingApi api) {
        this.api = api;
    }

    @Override
    public void reserveLimit(String operationId, String customerNo, BigDecimal amount) {
        run(() -> api.reserveLimit(new LimitRequest(operationId, customerNo, amount)));
    }

    @Override
    public void postAccounting(String operationId) {
        run(() -> api.postAccounting(new OperationRequest(operationId)));
    }

    @Override
    public void transfer(String operationId) {
        run(() -> api.transfer(new OperationRequest(operationId)));
    }

    @Override
    public void reverseAccounting(String operationId) {
        run(() -> api.reverseAccounting(new OperationRequest(operationId)));
    }

    @Override
    public void releaseLimit(String operationId) {
        run(() -> api.releaseLimit(new OperationRequest(operationId)));
    }

    @Override
    public boolean isCompleted(Step step, String operationId) {
        return call(() -> api.operationStatus(step.path(), operationId).completed());
    }

    private void run(Runnable request) {
        call(() -> {
            request.run();
            return null;
        });
    }

    /** Sorts failures by what they say about the work, which is what the saga has to decide on. */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (FeignException.UnprocessableEntity ex) {
            // The body holds the core's own reason, while the exception message also carries the URL
            throw new CoreRefusedException(ex.contentUTF8());
        } catch (RetryableException ex) {
            if (ex.getCause() instanceof ConnectException) {
                throw new CoreUnreachableException("Core could not be reached", ex);
            }
            throw new CoreOutcomeUnknownException("No answer from the core", ex);
        } catch (FeignException ex) {
            throw new CoreOutcomeUnknownException("Unexpected answer from the core: " + ex.status(), ex);
        }
    }
}
