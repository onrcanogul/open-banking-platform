package dev.obe.disbursement.api.controller;

import dev.obe.disbursement.infrastructure.batch.ReconciliationRunner;
import org.springframework.batch.core.BatchStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts reconciliation by hand, for operators and for trying it out. */
@RestController
class ReconciliationController {

    private final ReconciliationRunner runner;

    ReconciliationController(ReconciliationRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/internal/reconciliation")
    BatchStatus run() {
        return runner.run(true);
    }
}
