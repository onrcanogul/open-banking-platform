package dev.obe.subscription.infrastructure.directory;

import dev.obe.subscription.application.port.out.YosDirectory;
import dev.obe.subscription.application.port.out.YosProfile;
import dev.obe.subscription.infrastructure.config.ObeProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Stands in for the BKM YOS API until a real client exists. */
@Component
class ConfiguredYosDirectory implements YosDirectory {

    private final ObeProperties properties;

    ConfiguredYosDirectory(ObeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<YosProfile> find(String yosCode) {
        return Optional.ofNullable(properties.yosDirectory().get(yosCode));
    }
}
