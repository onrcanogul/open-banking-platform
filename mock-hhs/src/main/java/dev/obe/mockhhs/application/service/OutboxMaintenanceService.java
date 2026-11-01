package dev.obe.mockhhs.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxMaintenanceService {

    private static final int PAGE_SIZE = 500;
    private static final int CLEANUP_BATCH_SIZE = 1000;

    private final OutboxEventRepository repository;
    private final Duration retention;

    public OutboxMaintenanceService(OutboxEventRepository repository,
            @Value("${obe.outbox.retention}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    /** Returns one page of published events, oldest first. Pages start from 1. */
    @Transactional(readOnly = true)
    public List<NotificationEvent> findPublished(Instant from, Instant to, int page) {
        return repository.findPublished(from, to, PageRequest.of(page - 1, PAGE_SIZE)).stream()
                .map(OutboxEvent::toNotificationEvent)
                .toList();
    }

    /** Returns how many rows one batch removed, so the caller knows when to stop. */
    @Transactional
    public int deleteOldPublished(Instant now) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(now.minus(retention), ZoneOffset.UTC);
        return repository.deletePublishedBefore(cutoff, CLEANUP_BATCH_SIZE);
    }
}
