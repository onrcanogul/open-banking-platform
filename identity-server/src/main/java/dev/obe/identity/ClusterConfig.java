package dev.obe.identity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

/** What several instances must share. The defaults keep both of these inside one process. */
@Configuration
class ClusterConfig {

    /** The same key after a restart and on every instance, so issued tokens stay valid. */
    @Bean
    JWKSource<SecurityContext> signingKey(IdentityProperties properties)
            throws GeneralSecurityException, JOSEException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        byte[] encoded = Base64.getDecoder().decode(properties.signingKey());
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    OAuth2AuthorizationService authorizationService(RedisConnectionFactory connections, StringRedisTemplate tokens,
            IdentityProperties properties) {
        RedisTemplate<String, OAuth2Authorization> authorizations = new RedisTemplate<>();
        authorizations.setConnectionFactory(connections);
        authorizations.setKeySerializer(RedisSerializer.string());
        authorizations.setValueSerializer(new JdkSerializationRedisSerializer());
        authorizations.afterPropertiesSet();
        return new RedisAuthorizationService(authorizations, tokens, properties.authorizationTimeToLive());
    }
}
