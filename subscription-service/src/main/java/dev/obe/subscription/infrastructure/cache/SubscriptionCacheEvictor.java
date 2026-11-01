package dev.obe.subscription.infrastructure.cache;

import dev.obe.contract.subscription.SubscriptionCacheKeys;
import dev.obe.subscription.application.event.SubscriptionChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Clears a changed subscription from the dispatcher's cache. It runs after commit: clearing earlier
 * would let the dispatcher read the old row and put it back. If Redis is down the entry simply
 * expires by its time to live, so a failure here is logged and does not fail the request.
 */
@Component
class SubscriptionCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCacheEvictor.class);

    private final StringRedisTemplate redis;

    SubscriptionCacheEvictor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @TransactionalEventListener
    void onSubscriptionChanged(SubscriptionChanged event) {
        try {
            redis.delete(SubscriptionCacheKeys.of(event.yosCode()));
        } catch (DataAccessException ex) {
            log.warn("Could not clear the cached subscription of YOS {}: {}", event.yosCode(), ex.getMessage());
        }
    }
}
