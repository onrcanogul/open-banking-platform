package dev.obe.gateway;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param partnerApiKeys API key of each partner, by partner code */
@ConfigurationProperties("obe")
public record GatewayProperties(Map<String, String> partnerApiKeys) {
}
