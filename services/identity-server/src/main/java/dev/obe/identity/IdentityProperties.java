package dev.obe.identity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param users sample staff by username, standing in for a corporate directory
 * @param signingKey RSA private key that signs tokens, as Base64 PKCS#8
 * @param authorizationTimeToLive how long a login's record stays, longer than any token it holds
 */
@ConfigurationProperties("obe")
public record IdentityProperties(String uiOrigin, Map<String, User> users, String signingKey,
        Duration authorizationTimeToLive) {

    public record User(String password, List<String> roles) {
    }
}
