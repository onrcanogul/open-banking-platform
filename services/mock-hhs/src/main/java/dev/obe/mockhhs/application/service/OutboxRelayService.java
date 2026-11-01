package dev.obe.mockhhs.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.mockhhs.application.port.out.EventPublisher;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves pending outbox events to the broker. Delivery is at least once: a crash after the broker
 * accepts a batch but before this transaction commits publishes that batch again.
 */
@Service
public class OutboxRelayService {

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final int batchSize;

    public OutboxRelayService(OutboxEventRepository repository, EventPublisher publisher,
            @Value("${obe.outbox.batch-size}") int batchSize) {
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    /** Returns how many events this call published. */
    @Transactional
    public int publishPending() {
        List<String> eventNos = repository.findPendingEventNos(PageRequest.ofSize(batchSize));
        if (eventNos.isEmpty()) {
            return 0;
        }
        List<OutboxEvent> events = repository.lockPending(eventNos);
        if (events.isEmpty()) {
            return 0;
        }
        List<NotificationEvent> messages = events.stream().map(OutboxEvent::toNotificationEvent).toList();
        publisher.publish(messages);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        events.forEach(event -> event.markPublished(now));
        return events.size();
    }
}
