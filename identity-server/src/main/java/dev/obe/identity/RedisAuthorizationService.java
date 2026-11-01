package dev.obe.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Keeps logins in progress and issued tokens in Redis, so a code from one instance works on another.
 * A login is stored once, and each of its tokens points at it through a key holding the token's hash.
 */
class RedisAuthorizationService implements OAuth2AuthorizationService {

    private static final String AUTHORIZATION_PREFIX = "obe:identity:authorization:";
    private static final String TOKEN_PREFIX = "obe:identity:token:";

    private final RedisTemplate<String, OAuth2Authorization> authorizations;
    private final StringRedisTemplate tokens;
    private final Duration timeToLive;

    RedisAuthorizationService(RedisTemplate<String, OAuth2Authorization> authorizations, StringRedisTemplate tokens,
            Duration timeToLive) {
        this.authorizations = authorizations;
        this.tokens = tokens;
        this.timeToLive = timeToLive;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        authorizations.opsForValue().set(authorizationKey(authorization.getId()), authorization, timeToLive);
        tokenValuesOf(authorization)
                .forEach(value -> tokens.opsForValue().set(tokenKey(value), authorization.getId(), timeToLive));
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        authorizations.delete(authorizationKey(authorization.getId()));
        tokens.delete(tokenValuesOf(authorization).stream().map(this::tokenKey).toList());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return authorizations.opsForValue().get(authorizationKey(id));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String id = tokens.opsForValue().get(tokenKey(token));
        return id == null ? null : findById(id);
    }

    private List<String> tokenValuesOf(OAuth2Authorization authorization) {
        List<String> values = new ArrayList<>();
        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        if (state != null) {
            values.add(state);
        }
        addToken(values, authorization, OAuth2AuthorizationCode.class);
        addToken(values, authorization, OAuth2AccessToken.class);
        addToken(values, authorization, OAuth2RefreshToken.class);
        addToken(values, authorization, OidcIdToken.class);
        return values;
    }

    private void addToken(List<String> values, OAuth2Authorization authorization,
            Class<? extends AbstractOAuth2Token> type) {
        OAuth2Authorization.Token<?> token = authorization.getToken(type);
        if (token != null) {
            values.add(token.getToken().getTokenValue());
        }
    }

    private String authorizationKey(String id) {
        return AUTHORIZATION_PREFIX + id;
    }

    // The hash keeps a usable token out of key listings
    private String tokenKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return TOKEN_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", ex);
        }
    }
}
