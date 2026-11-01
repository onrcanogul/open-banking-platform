package dev.obe.subscription.infrastructure.config;

import dev.obe.subscription.application.port.out.YosProfile;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("obe")
public record ObeProperties(Map<String, YosProfile> yosDirectory) {
}
