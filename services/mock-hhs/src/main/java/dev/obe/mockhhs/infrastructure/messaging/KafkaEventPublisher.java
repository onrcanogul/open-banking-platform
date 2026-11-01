package dev.obe.mockhhs.infrastructure.messaging;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.mockhhs.application.port.out.EventPublishException;
import dev.obe.mockhhs.application.port.out.EventPublisher;
import dev.obe.observability.Correlation;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, NotificationEvent> kafka;
    private final Duration sendTimeout;

    KafkaEventPublisher(KafkaTemplate<String, NotificationEvent> kafka,
            @Value("${obe.outbox.send-timeout}") Duration sendTimeout) {
        this.kafka = kafka;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void publish(List<NotificationEvent> events) {
        // Keyed by resource so all events of one resource stay in order on the same partition
        CompletableFuture<?>[] sends = events.stream()
                .map(event -> kafka.send(NotificationTopics.EVENTS, event.resourceNo(), event))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while publishing events", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new EventPublishException("Broker did not accept the events", ex);
        }
        // The relay runs on its own thread, so each event brings back the id of its request
        events.forEach(event -> Correlation.run(event.correlationId(),
                () -> log.info("Published event {} for {} {}", event.eventNo(), event.resourceType(),
                        event.resourceNo())));
    }
}
