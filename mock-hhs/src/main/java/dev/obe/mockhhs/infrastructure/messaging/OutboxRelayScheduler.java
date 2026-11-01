package dev.obe.mockhhs.infrastructure.messaging;

import dev.obe.mockhhs.application.service.OutboxRelayService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter published;
    private final Counter failures;

    OutboxRelayScheduler(OutboxRelayService relay, MeterRegistry registry) {
        this.relay = relay;
        this.published = registry.counter("obe.outbox.published");
        this.failures = registry.counter("obe.outbox.relay.failures");
    }

    @Scheduled(fixedDelayString = "${obe.outbox.poll-interval}")
    void run() {
        try {
            int count = relay.publishPending();
            while (count > 0) {
                published.increment(count);
                count = relay.publishPending();
            }
        } catch (RuntimeException ex) {
            failures.increment();
            // Events stay pending, so the next run retries them
            log.error("Outbox relay failed", ex);
        }
    }
}
