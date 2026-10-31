package dev.obe.dispatcher.infrastructure.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("obe")
public record DispatcherProperties(String hhsCode, Map<String, String> listenerUrls) {
}
