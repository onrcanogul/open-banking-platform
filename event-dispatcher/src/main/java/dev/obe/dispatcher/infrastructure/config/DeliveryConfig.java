package dev.obe.dispatcher.infrastructure.config;

import dev.obe.dispatcher.domain.model.DeliveryStatus;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
class DeliveryConfig {

    /** Read from the database on each scrape, so it stays correct across restarts and instances. */
    @Bean
    MeterBinder pendingDeliveriesGauge(EventDeliveryRepository repository) {
        return registry -> Gauge.builder("obe.delivery.pending", repository,
                        deliveries -> deliveries.countByStatus(DeliveryStatus.PENDING))
                .register(registry);
    }

    @Bean
    Executor deliveryExecutor(@Value("${obe.delivery.concurrent-calls}") int concurrentCalls) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrentCalls);
        executor.setMaxPoolSize(concurrentCalls);
        executor.setThreadNamePrefix("delivery-");
        return executor;
    }
}
