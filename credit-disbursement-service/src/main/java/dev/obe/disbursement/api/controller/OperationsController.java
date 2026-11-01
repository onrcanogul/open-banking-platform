package dev.obe.disbursement.api.controller;

import dev.obe.disbursement.application.result.DisbursementSummary;
import dev.obe.disbursement.application.result.FindingResult;
import dev.obe.disbursement.application.service.DisbursementService;
import dev.obe.disbursement.infrastructure.batch.FindingQuery;
import dev.obe.disbursement.infrastructure.batch.ReconciliationRunner;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.batch.core.BatchStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The staff panel's view of all partners. The gateway checks the caller's role before this runs. */
@RestController
@RequestMapping("/operations")
class OperationsController {

    private static final int PAGE_SIZE = 50;

    private final DisbursementService service;
    private final FindingQuery findings;
    private final ReconciliationRunner runner;

    OperationsController(DisbursementService service, FindingQuery findings, ReconciliationRunner runner) {
        this.service = service;
        this.findings = findings;
        this.runner = runner;
    }

    @GetMapping("/disbursements")
    List<DisbursementSummary> disbursements(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return service.latest(page, PAGE_SIZE);
    }

    @GetMapping("/findings")
    List<FindingResult> findings(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return findings.latest(page, PAGE_SIZE);
    }

    @PostMapping("/reconciliation")
    BatchStatus reconcile() {
        return runner.run(true);
    }
}
