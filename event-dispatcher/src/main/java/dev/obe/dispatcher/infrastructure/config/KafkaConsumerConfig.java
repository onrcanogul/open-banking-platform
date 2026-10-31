package dev.obe.dispatcher.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfig {

    /**
     * The default handler gives up after a few instant retries and discards the records, which loses
     * events during a short outage. Retrying without limit holds the partition instead. Only this
     * service's own dependencies can fail here, since calls to a YOS happen after intake.
     */
    @Bean
    CommonErrorHandler consumerErrorHandler(@Value("${obe.redelivery-interval}") Duration interval) {
        return new DefaultErrorHandler(new FixedBackOff(interval.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
