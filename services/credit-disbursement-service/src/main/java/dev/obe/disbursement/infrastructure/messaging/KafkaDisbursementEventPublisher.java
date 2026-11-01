package dev.obe.disbursement.infrastructure.messaging;

import dev.obe.contract.event.DisbursementEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.disbursement.application.port.out.DisbursementEventPublisher;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class KafkaDisbursementEventPublisher implements DisbursementEventPublisher {

    private final KafkaTemplate<String, DisbursementEvent> kafka;
    private final Duration sendTimeout;

    KafkaDisbursementEventPublisher(KafkaTemplate<String, DisbursementEvent> kafka,
            @Value("${obe.outbox.send-timeout}") Duration sendTimeout) {
        this.kafka = kafka;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void publish(List<DisbursementEvent> events) {
        // Keyed by disbursement, whose single event needs no order against other disbursements
        CompletableFuture<?>[] sends = events.stream()
                .map(event -> kafka.send(NotificationTopics.DISBURSEMENTS, event.disbursementNo(), event))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing events", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Broker did not accept the events", ex);
        }
    }
}
