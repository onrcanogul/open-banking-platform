package dev.obe.gateway;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param partnerApiKeys API key of each partner, by partner code
 * @param uiOrigin the only site whose scripts may call this gateway
 */
@ConfigurationProperties("obe")
public record GatewayProperties(Map<String, String> partnerApiKeys, String uiOrigin) {
}
