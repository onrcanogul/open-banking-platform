package dev.obe.mockhhs.infrastructure.config;

import dev.obe.contract.event.NotificationTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class MessagingConfig {

    @Bean
    NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NotificationTopics.EVENTS).partitions(3).replicas(1).build();
    }
}
