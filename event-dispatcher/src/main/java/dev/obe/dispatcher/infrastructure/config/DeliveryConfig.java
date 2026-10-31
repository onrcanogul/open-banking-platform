package dev.obe.dispatcher.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
class DeliveryConfig {

    @Bean
    Executor deliveryExecutor(@Value("${obe.delivery.concurrent-calls}") int concurrentCalls) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrentCalls);
        executor.setMaxPoolSize(concurrentCalls);
        executor.setThreadNamePrefix("delivery-");
        return executor;
    }
}
