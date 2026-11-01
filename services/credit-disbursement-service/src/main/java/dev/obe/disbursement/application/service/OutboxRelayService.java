package dev.obe.disbursement.application.service;

import dev.obe.disbursement.application.port.out.DisbursementEventPublisher;
import dev.obe.disbursement.domain.model.DisbursementOutboxEvent;
import dev.obe.disbursement.domain.repository.DisbursementOutboxRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves pending events to the broker. A crash before commit publishes them again, at least once. */
@Service
public class OutboxRelayService {

    private final DisbursementOutboxRepository repository;
    private final DisbursementEventPublisher publisher;
    private final int batchSize;

    public OutboxRelayService(DisbursementOutboxRepository repository, DisbursementEventPublisher publisher,
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
        List<DisbursementOutboxEvent> events = repository.lockPending(eventNos);
        if (events.isEmpty()) {
            return 0;
        }
        publisher.publish(events.stream().map(DisbursementOutboxEvent::toEvent).toList());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        events.forEach(event -> event.markPublished(now));
        return events.size();
    }
}
