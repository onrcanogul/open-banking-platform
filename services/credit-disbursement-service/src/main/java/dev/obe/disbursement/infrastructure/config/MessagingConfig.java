package dev.obe.disbursement.infrastructure.config;

import dev.obe.contract.event.NotificationTopics;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class MessagingConfig {

    @Bean
    NewTopic disbursementEventsTopic() {
        return TopicBuilder.name(NotificationTopics.DISBURSEMENTS).partitions(3).replicas(1).build();
    }

    /** The default handler discards records after a few instant retries, which would lose the event. */
    @Bean
    CommonErrorHandler consumerErrorHandler(
            @Value("${obe.notification.redelivery-interval}") Duration interval) {
        return new DefaultErrorHandler(new FixedBackOff(interval.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
