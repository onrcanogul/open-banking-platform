package dev.obe.mockcore;

import dev.obe.mockcore.FaultInjector.Mode;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CoreController {

    private static final String LIMIT = "limit";
    private static final String ACCOUNTING = "accounting";
    private static final String TRANSFER = "transfer";
    private static final String LIMIT_RELEASE = "limit-release";
    private static final String ACCOUNTING_REVERSAL = "accounting-reversal";

    private final CoreLedger ledger;
    private final FaultInjector faults;

    CoreController(CoreLedger ledger, FaultInjector faults) {
        this.ledger = ledger;
        this.faults = faults;
    }

    record LimitRequest(String operationId, String customerNo, BigDecimal amount) {
    }

    record OperationRequest(String operationId) {
    }

    record OperationStatus(boolean completed) {
    }

    @PostMapping("/core/limits/reservations")
    ResponseEntity<?> reserveLimit(@RequestBody LimitRequest request) {
        return run(LIMIT, () -> ledger.reserveLimit(request.operationId(), request.customerNo(), request.amount()));
    }

    @PostMapping("/core/limits/releases")
    ResponseEntity<?> releaseLimit(@RequestBody OperationRequest request) {
        return run(LIMIT_RELEASE, () -> {
            ledger.releaseLimit(request.operationId());
            return true;
        });
    }

    @PostMapping("/core/accounting/entries")
    ResponseEntity<?> postAccounting(@RequestBody OperationRequest request) {
        return run(ACCOUNTING, () -> {
            ledger.postAccounting(request.operationId());
            return true;
        });
    }

    @PostMapping("/core/accounting/reversals")
    ResponseEntity<?> reverseAccounting(@RequestBody OperationRequest request) {
        return run(ACCOUNTING_REVERSAL, () -> {
            ledger.reverseAccounting(request.operationId());
            return true;
        });
    }

    @PostMapping("/core/transfers")
    ResponseEntity<?> transfer(@RequestBody OperationRequest request) {
        return run(TRANSFER, () -> {
            ledger.transfer(request.operationId());
            return true;
        });
    }

    @GetMapping("/core/operations/{step}/{operationId}")
    OperationStatus operationStatus(@PathVariable String step, @PathVariable String operationId) {
        return new OperationStatus(ledger.isCompleted(step, operationId));
    }

    @GetMapping("/internal/limits/{customerNo}")
    BigDecimal availableLimit(@PathVariable String customerNo) {
        return ledger.availableLimit(customerNo);
    }

    @GetMapping("/internal/history")
    List<String> history() {
        return ledger.history();
    }

    @PostMapping("/internal/faults/{step}")
    void setFault(@PathVariable String step, @RequestParam Mode mode) {
        faults.set(step, mode);
    }

    @DeleteMapping("/internal/faults")
    void clearFaults() {
        faults.clear();
    }

    /** A 422 is a business refusal: the call worked and the answer is no. */
    private ResponseEntity<?> run(String step, BooleanSupplier work) {
        Mode fault = faults.modeOf(step);
        if (fault == Mode.REFUSE) {
            return refusal("Refused by a test fault");
        }
        if (fault == Mode.HANG) {
            faults.hang();
            return ResponseEntity.ok().build();
        }
        boolean accepted = work.getAsBoolean();
        if (fault == Mode.DO_THEN_HANG) {
            faults.hang();
        }
        return accepted ? ResponseEntity.ok().build() : refusal("Limit is not enough");
    }

    private ResponseEntity<ProblemDetail> refusal(String reason) {
        return ResponseEntity.unprocessableContent()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, reason));
    }
}
