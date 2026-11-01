package dev.obe.dispatcher.infrastructure.messaging;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.dispatcher.application.service.EventIntakeService;
import dev.obe.observability.Correlation;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Offsets are committed after this method returns, so an exception means the read is redelivered. */
@Component
class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final EventIntakeService intake;

    NotificationEventConsumer(EventIntakeService intake) {
        this.intake = intake;
    }

    @KafkaListener(topics = NotificationTopics.EVENTS)
    void consume(List<NotificationEvent> messages) {
        List<NotificationEvent> events = messages.stream().filter(Objects::nonNull).toList();
        if (events.size() != messages.size()) {
            log.error("Skipped {} unreadable messages", messages.size() - events.size());
        }
        intake.accept(events);
        // The listener thread has no request, so each event brings back the id of its own
        events.forEach(event -> Correlation.run(event.correlationId(),
                () -> log.info("Accepted event {} for YOS {}", event.eventNo(), event.yosCode())));
    }
}
