package dev.obe.dispatcher.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.domain.model.DeliveryStatus;
import dev.obe.dispatcher.domain.model.EventDelivery;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database steps of a delivery, each in its own short transaction. They live apart from the sender
 * because a call within one class skips the transaction proxy, and because no transaction may stay
 * open during a call to a YOS.
 */
@Service
public class DeliveryStore {

    private final EventDeliveryRepository repository;
    private final int claimSize;
    private final Duration claimLease;

    public DeliveryStore(EventDeliveryRepository repository, @Value("${obe.delivery.claim-size}") int claimSize,
            @Value("${obe.delivery.claim-lease}") Duration claimLease) {
        this.repository = repository;
        this.claimSize = claimSize;
        this.claimLease = claimLease;
    }

    /** Skips events that already have a record, so a redelivered Kafka message changes nothing. */
    @Transactional
    public void saveNew(List<NotificationEvent> events) {
        List<String> eventNos = events.stream().map(NotificationEvent::eventNo).toList();
        Set<String> known = repository.findAllById(eventNos).stream()
                .map(EventDelivery::getEventNo)
                .collect(Collectors.toSet());
        Instant now = now();
        List<EventDelivery> deliveries = events.stream()
                .filter(event -> !known.contains(event.eventNo()))
                .map(event -> EventDelivery.create(event, now))
                .toList();
        repository.saveAll(deliveries);
    }

    /** Takes due rows for this sender. The lease returns them to the queue if the sender dies. */
    @Transactional
    public List<NotificationEvent> claimDue() {
        Instant now = now();
        List<String> eventNos = repository.findDueEventNos(now, PageRequest.ofSize(claimSize));
        if (eventNos.isEmpty()) {
            return List.of();
        }
        List<EventDelivery> deliveries = repository.lockDue(eventNos, now);
        deliveries.forEach(delivery -> delivery.claimUntil(now.plus(claimLease)));
        return deliveries.stream().map(EventDelivery::toNotificationEvent).toList();
    }

    @Transactional
    public void markDelivered(List<String> eventNos) {
        Instant now = now();
        repository.findAllById(eventNos).forEach(delivery -> delivery.markDelivered(now));
    }

    @Transactional
    public void recordFailure(List<String> eventNos, String error) {
        Instant now = now();
        for (EventDelivery delivery : repository.findAllById(eventNos)) {
            delivery.recordFailure(error, now);
            if (delivery.getStatus() == DeliveryStatus.UNDELIVERED) {
                repository.supersedeUndelivered(delivery.getYosCode(), delivery.getEventType(),
                        delivery.getResourceType(), delivery.getResourceNo(), delivery.getEventNo());
            }
        }
    }

    // Trimmed to the column's millisecond precision so comparisons match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
