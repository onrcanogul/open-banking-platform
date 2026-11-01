package dev.obe.dispatcher.infrastructure.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "obe.batch.scheduler-enabled", havingValue = "true")
class BatchJobScheduler {

    private static final String BANK_ZONE = "Europe/Istanbul";

    private final BatchJobRunner runner;

    BatchJobScheduler(BatchJobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${obe.batch.archive.cron}", zone = BANK_ZONE)
    void archive() {
        runner.runArchive(false);
    }

    @Scheduled(cron = "${obe.batch.reconciliation.cron}", zone = BANK_ZONE)
    void reconcile() {
        runner.runReconciliation(false);
    }
}
