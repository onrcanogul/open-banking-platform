package dev.obe.disbursement.infrastructure.saga;

import dev.obe.disbursement.application.service.DisbursementSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "obe.saga.worker-enabled", havingValue = "true")
class SagaWorker {

    private static final Logger log = LoggerFactory.getLogger(SagaWorker.class);

    private final DisbursementSaga saga;

    SagaWorker(DisbursementSaga saga) {
        this.saga = saga;
    }

    @Scheduled(fixedDelayString = "${obe.saga.poll-interval}")
    void run() {
        try {
            while (saga.advanceDue() > 0) {
                log.debug("Advanced a batch of disbursements");
            }
        } catch (RuntimeException ex) {
            log.error("Saga worker round failed", ex);
        }
    }
}
