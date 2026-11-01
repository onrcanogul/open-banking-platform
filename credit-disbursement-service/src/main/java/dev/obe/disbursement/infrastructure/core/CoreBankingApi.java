package dev.obe.disbursement.infrastructure.core;

import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "core-banking", url = "${obe.core-banking-url}")
interface CoreBankingApi {

    record LimitRequest(String operationId, String customerNo, BigDecimal amount) {
    }

    record OperationRequest(String operationId) {
    }

    @PostMapping("/core/limits/reservations")
    void reserveLimit(@RequestBody LimitRequest request);

    @PostMapping("/core/accounting/entries")
    void postAccounting(@RequestBody OperationRequest request);

    @PostMapping("/core/transfers")
    void transfer(@RequestBody OperationRequest request);
}
