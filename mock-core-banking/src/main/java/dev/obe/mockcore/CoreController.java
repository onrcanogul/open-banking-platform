package dev.obe.mockcore;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CoreController {

    private final CoreLedger ledger;

    CoreController(CoreLedger ledger) {
        this.ledger = ledger;
    }

    record LimitRequest(String operationId, String customerNo, BigDecimal amount) {
    }

    record OperationRequest(String operationId) {
    }

    /** A 422 is a business refusal: the call worked and the answer is no. */
    @PostMapping("/core/limits/reservations")
    ResponseEntity<?> reserveLimit(@RequestBody LimitRequest request) {
        if (ledger.reserveLimit(request.operationId(), request.customerNo(), request.amount())) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.unprocessableContent()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Limit is not enough"));
    }

    @PostMapping("/core/accounting/entries")
    void postAccounting(@RequestBody OperationRequest request) {
        ledger.postAccounting(request.operationId());
    }

    @PostMapping("/core/transfers")
    void transfer(@RequestBody OperationRequest request) {
        ledger.transfer(request.operationId());
    }

    @GetMapping("/internal/limits/{customerNo}")
    BigDecimal availableLimit(@PathVariable String customerNo) {
        return ledger.availableLimit(customerNo);
    }

    @GetMapping("/internal/history")
    List<String> history() {
        return ledger.history();
    }
}
