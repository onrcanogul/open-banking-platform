package dev.obe.mockhhs.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.model.OutboxStatus;
import dev.obe.mockhhs.domain.model.PaymentOrder;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import dev.obe.mockhhs.domain.repository.PaymentOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_HHS_USER=unused", "ORACLE_HHS_PASSWORD=unused",
        "spring.kafka.admin.auto-create=false", "obe.outbox.relay-enabled=false"})
@Testcontainers
class OutboxMaintenanceServiceTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private OutboxMaintenanceService service;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private PaymentOrderRepository orderRepository;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void listsOnlyPublishedEventsInTheRange() {
        OutboxEvent inRange = published(NOW.minusSeconds(60));
        published(NOW.minusSeconds(6000));
        pending(NOW.minusSeconds(60));

        List<NotificationEvent> events = service.findPublished(NOW.minusSeconds(600), NOW, 1);

        assertThat(events).extracting(NotificationEvent::eventNo).containsExactly(inRange.getEventNo());
    }

    @Test
    void deletesOnlyOldPublishedEvents() {
        OutboxEvent recent = published(NOW.minus(Duration.ofDays(1)));
        OutboxEvent stillPending = pending(NOW.minus(Duration.ofDays(30)));
        published(NOW.minus(Duration.ofDays(30)));

        int removed = service.deleteOldPublished(NOW);

        assertThat(removed).isEqualTo(1);
        assertThat(outboxRepository.findAll()).extracting(OutboxEvent::getEventNo)
                .containsExactlyInAnyOrder(recent.getEventNo(), stillPending.getEventNo());
    }

    @Test
    void stopsWhenNothingIsLeft() {
        assertThat(service.deleteOldPublished(NOW)).isZero();
    }

    private OutboxEvent pending(Instant occurredAt) {
        PaymentOrder order = orderRepository.save(PaymentOrder.create("8001", new BigDecimal("10.00"), occurredAt));
        OutboxEvent event = outboxRepository.save(OutboxEvent.paymentOrderUpdated(order, occurredAt, null));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        return event;
    }

    private OutboxEvent published(Instant publishedAt) {
        OutboxEvent event = pending(publishedAt);
        event.markPublished(publishedAt);
        return outboxRepository.save(event);
    }
}
