package dev.obe.disbursement.infrastructure.config;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("obe")
public record PartnerProperties(Map<String, Partner> partners) {

    public record Partner(BigDecimal maxAmount, String webhookUrl) {
    }
}
