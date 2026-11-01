package dev.obe.dispatcher.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void newDeliveryIsDueImmediately() {
        EventDelivery delivery = delivery(ResourceType.ODEME_EMRI);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void failureSchedulesNextAttempt() {
        EventDelivery delivery = delivery(ResourceType.ODEME_EMRI);

        delivery.recordFailure("503", NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getFailedAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30).dividedBy(7)));
    }

    @Test
    void becomesUndeliveredWhenRetriesRunOut() {
        EventDelivery delivery = delivery(ResourceType.ODEME_EMRI);

        delivery.recordFailure("first attempt", NOW);
        delivery.recordFailure("retry 1", NOW);
        delivery.recordFailure("retry 2", NOW);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);

        delivery.recordFailure("retry 3", NOW);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.UNDELIVERED);
    }

    @Test
    void balanceBecomesUndeliveredOnFirstFailure() {
        EventDelivery delivery = delivery(ResourceType.BAKIYE);

        delivery.recordFailure("503", NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.UNDELIVERED);
    }

    @Test
    void successAfterFailureEndsAsDelivered() {
        EventDelivery delivery = delivery(ResourceType.ODEME_EMRI);

        delivery.recordFailure("503", NOW);
        delivery.markDelivered(NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void acceptsErrorLongerThanTheColumn() {
        EventDelivery delivery = delivery(ResourceType.BAKIYE);

        delivery.recordFailure("x".repeat(1000), NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.UNDELIVERED);
    }

    private EventDelivery delivery(ResourceType resourceType) {
        NotificationEvent event = new NotificationEvent("event-1", "8001", EventType.KAYNAK_GUNCELLENDI, resourceType,
                "resource-1", NOW);
        return EventDelivery.create(event, NOW);
    }
}
