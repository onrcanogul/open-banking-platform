package dev.obe.mockhhs.infrastructure.messaging;

import dev.obe.mockhhs.application.service.OutboxMaintenanceService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A plain delete needs no progress tracking, so this does not use Spring Batch. */
@Component
@ConditionalOnProperty(name = "obe.outbox.relay-enabled", havingValue = "true")
class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

    private final OutboxMaintenanceService maintenance;

    OutboxCleanupScheduler(OutboxMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(cron = "${obe.outbox.cleanup-cron}", zone = "Europe/Istanbul")
    void run() {
        Instant now = Instant.now();
        int total = 0;
        int removed = maintenance.deleteOldPublished(now);
        while (removed > 0) {
            total += removed;
            removed = maintenance.deleteOldPublished(now);
        }
        log.info("Outbox cleanup removed {} events", total);
    }
}
