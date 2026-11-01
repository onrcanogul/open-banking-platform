package dev.obe.dispatcher.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MicrometerDeliveryMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerDeliveryMetrics metrics = new MicrometerDeliveryMetrics(registry);

    @Test
    void countsDeliveredAndFailedAttemptsSeparately() {
        metrics.delivered(event("event-1", "resource-1"));
        metrics.failed(event("event-2", "resource-2"));
        metrics.failed(event("event-3", "resource-3"));

        assertThat(registry.get("obe.delivery.attempts").tag("result", "delivered").counter().count()).isEqualTo(1);
        assertThat(registry.get("obe.delivery.attempts").tag("result", "failed").counter().count()).isEqualTo(2);
    }

    @Test
    void countsUndeliveredEvents() {
        metrics.undelivered(event("event-1", "resource-1"));

        assertThat(registry.get("obe.delivery.undelivered").tag("yos", "8001").counter().count()).isEqualTo(1);
    }

    @Test
    void measuresTimeSinceTheEventOccurred() {
        metrics.delivered(event("event-1", "resource-1"));

        assertThat(registry.get("obe.delivery.latency").timer().count()).isEqualTo(1);
    }

    @Test
    void manyEventsCreateNoNewMeters() {
        metrics.delivered(event("event-1", "resource-1"));
        int meters = registry.getMeters().size();

        metrics.delivered(event("event-2", "resource-2"));

        assertThat(registry.getMeters()).hasSize(meters);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(tagValues(meter))
                .doesNotContain("event-1", "resource-1"));
    }

    private List<String> tagValues(Meter meter) {
        return meter.getId().getTags().stream().map(tag -> tag.getValue()).toList();
    }

    private NotificationEvent event(String eventNo, String resourceNo) {
        return new NotificationEvent(eventNo, "8001", EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI,
                resourceNo, Instant.now());
    }
}
