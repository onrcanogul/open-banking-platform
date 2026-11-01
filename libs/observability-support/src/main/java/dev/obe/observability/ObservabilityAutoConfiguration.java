package dev.obe.observability;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Applies to any service that has this module on its classpath, with no code in the service. */
@AutoConfiguration
@ConditionalOnWebApplication
public class ObservabilityAutoConfiguration {

    @Bean
    CorrelationFilter correlationFilter() {
        return new CorrelationFilter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class FeignCorrelation {

        /** Carries the id across calls between our own services. */
        @Bean
        RequestInterceptor correlationInterceptor() {
            return template -> {
                String correlationId = Correlation.current();
                if (correlationId != null) {
                    template.header(Correlation.HEADER, correlationId);
                }
            };
        }
    }
}
