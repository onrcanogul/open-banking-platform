package dev.obe.disbursement.infrastructure.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "obe.reconciliation.scheduler-enabled", havingValue = "true")
class ReconciliationScheduler {

    private final ReconciliationRunner runner;

    ReconciliationScheduler(ReconciliationRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${obe.reconciliation.cron}", zone = "Europe/Istanbul")
    void run() {
        runner.run(false);
    }
}
