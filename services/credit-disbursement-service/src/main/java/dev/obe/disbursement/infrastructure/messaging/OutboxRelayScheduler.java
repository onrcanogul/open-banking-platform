package dev.obe.disbursement.infrastructure.messaging;

import dev.obe.disbursement.application.service.OutboxRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "obe.outbox.relay-enabled", havingValue = "true")
class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelayService relay;

    OutboxRelayScheduler(OutboxRelayService relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${obe.outbox.poll-interval}")
    void run() {
        try {
            while (relay.publishPending() > 0) {
                log.debug("Published a batch of disbursement events");
            }
        } catch (RuntimeException ex) {
            // Events stay pending, so the next run retries them
            log.error("Outbox relay failed", ex);
        }
    }
}
