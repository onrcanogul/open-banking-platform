package dev.obe.mockcore;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("obe")
public record CoreProperties(Map<String, BigDecimal> customerLimits) {
}
