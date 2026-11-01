package dev.obe.mockhhs.infrastructure.config;

import dev.obe.contract.event.NotificationTopics;
import dev.obe.mockhhs.domain.model.OutboxStatus;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class MessagingConfig {

    /** Rises while the relay cannot reach Kafka, so it shows an outage before anyone complains. */
    @Bean
    MeterBinder pendingOutboxGauge(OutboxEventRepository repository) {
        return registry -> Gauge.builder("obe.outbox.pending", repository,
                        events -> events.countByStatus(OutboxStatus.PENDING))
                .register(registry);
    }

    @Bean
    NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NotificationTopics.EVENTS).partitions(3).replicas(1).build();
    }
}
