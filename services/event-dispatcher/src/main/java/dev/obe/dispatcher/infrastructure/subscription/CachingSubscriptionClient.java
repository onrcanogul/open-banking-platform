package dev.obe.dispatcher.infrastructure.subscription;

import dev.obe.contract.subscription.SubscriptionCacheKeys;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps subscriptions in Redis so that most reads skip the remote call. Redis failures are logged
 * and ignored, because the cache only saves time and the subscription service still has the data.
 */
@Primary
@Component
class CachingSubscriptionClient implements SubscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(CachingSubscriptionClient.class);

    // Remembers "no subscription" too, or such a YOS would reach the service on every event
    private static final String NOT_SUBSCRIBED = "none";

    private final FeignSubscriptionClient delegate;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;
    private final Duration notSubscribedTtl;

    CachingSubscriptionClient(FeignSubscriptionClient delegate, StringRedisTemplate redis, JsonMapper json,
            @Value("${obe.subscription-cache.ttl}") Duration ttl,
            @Value("${obe.subscription-cache.not-subscribed-ttl}") Duration notSubscribedTtl) {
        this.delegate = delegate;
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.notSubscribedTtl = notSubscribedTtl;
    }

    @Override
    public Optional<SubscriptionView> find(String yosCode) {
        String key = SubscriptionCacheKeys.of(yosCode);
        String cached = read(key);
        if (cached != null) {
            return NOT_SUBSCRIBED.equals(cached)
                    ? Optional.empty()
                    : Optional.of(json.readValue(cached, SubscriptionView.class));
        }
        Optional<SubscriptionView> subscription = delegate.find(yosCode);
        subscription.ifPresentOrElse(
                view -> write(key, json.writeValueAsString(view), ttl),
                () -> write(key, NOT_SUBSCRIBED, notSubscribedTtl));
        return subscription;
    }

    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.warn("Could not read the subscription cache: {}", ex.getMessage());
            return null;
        }
    }

    private void write(String key, String value, Duration timeToLive) {
        try {
            redis.opsForValue().set(key, value, timeToLive);
        } catch (DataAccessException ex) {
            log.warn("Could not write the subscription cache: {}", ex.getMessage());
        }
    }
}
