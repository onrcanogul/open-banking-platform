package dev.obe.identity;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param users sample staff by username, standing in for a corporate directory */
@ConfigurationProperties("obe")
public record IdentityProperties(String uiOrigin, Map<String, User> users) {

    public record User(String password, List<String> roles) {
    }
}
