package dev.obe.dispatcher.infrastructure.messaging;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.dispatcher.application.service.EventDispatchService;
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

    private final EventDispatchService service;

    NotificationEventConsumer(EventDispatchService service) {
        this.service = service;
    }

    @KafkaListener(topics = NotificationTopics.EVENTS)
    void consume(List<NotificationEvent> messages) {
        List<NotificationEvent> events = messages.stream().filter(Objects::nonNull).toList();
        if (events.size() != messages.size()) {
            log.error("Skipped {} unreadable messages", messages.size() - events.size());
        }
        service.dispatch(events);
    }
}
