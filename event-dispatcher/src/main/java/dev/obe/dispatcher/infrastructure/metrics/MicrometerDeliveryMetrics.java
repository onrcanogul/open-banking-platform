package dev.obe.dispatcher.infrastructure.metrics;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.DeliveryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Tags use only values from a small fixed set. Event or order numbers belong in logs: each new tag
 * value creates a new time series, which would soon overwhelm the metrics store.
 */
@Component
class MicrometerDeliveryMetrics implements DeliveryMetrics {

    private static final String ATTEMPTS = "obe.delivery.attempts";
    private static final String UNDELIVERED = "obe.delivery.undelivered";
    private static final String LATENCY = "obe.delivery.latency";

    /** The standard's limit for events it calls instant. */
    private static final Duration INSTANT_LIMIT = Duration.ofSeconds(5);

    private final MeterRegistry registry;

    MicrometerDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void delivered(NotificationEvent event) {
        registry.counter(ATTEMPTS, tags(event).and("result", "delivered")).increment();
        Timer.builder(LATENCY)
                .tags(eventTags(event))
                .serviceLevelObjectives(INSTANT_LIMIT)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.between(event.occurredAt(), Instant.now()));
    }

    @Override
    public void failed(NotificationEvent event) {
        registry.counter(ATTEMPTS, tags(event).and("result", "failed")).increment();
    }

    @Override
    public void undelivered(NotificationEvent event) {
        registry.counter(UNDELIVERED, tags(event)).increment();
    }

    private Tags tags(NotificationEvent event) {
        return eventTags(event).and("yos", event.yosCode());
    }

    private Tags eventTags(NotificationEvent event) {
        return Tags.of("event_type", event.eventType().name(), "resource_type", event.resourceType().name());
    }
}
