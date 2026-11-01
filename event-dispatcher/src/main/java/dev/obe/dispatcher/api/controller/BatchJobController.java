package dev.obe.dispatcher.api.controller;

import dev.obe.dispatcher.infrastructure.batch.BatchJobRunner;
import org.springframework.batch.core.BatchStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts a job by hand, for operators and for trying a job out. */
@RestController
@RequestMapping("/internal/batch-jobs")
class BatchJobController {

    private final BatchJobRunner runner;

    BatchJobController(BatchJobRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/archive")
    BatchStatus archive() {
        return runner.runArchive(true);
    }

    @PostMapping("/reconciliation")
    BatchStatus reconcile() {
        return runner.runReconciliation(true);
    }
}
