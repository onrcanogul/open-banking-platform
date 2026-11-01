package dev.obe.subscription.infrastructure.cache;

import dev.obe.subscription.application.port.out.UndeliveredQueryLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A key that exists means the YOS used its allowance. Setting it is one atomic step, so two queries
 * at the same moment cannot both pass. Redis failures let the query through: the standard makes this
 * limit optional, and a YOS should not lose access to its events because of a limiter.
 */
@Component
class RedisUndeliveredQueryLimiter implements UndeliveredQueryLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisUndeliveredQueryLimiter.class);
    private static final String PREFIX = "obe:undelivered-limit:";

    private final StringRedisTemplate redis;
    private final Duration interval;

    RedisUndeliveredQueryLimiter(StringRedisTemplate redis,
            @Value("${obe.undelivered.query-interval}") Duration interval) {
        this.redis = redis;
        this.interval = interval;
    }

    @Override
    public long acquire(String yosCode) {
        String key = PREFIX + yosCode;
        try {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", interval))) {
                return 0;
            }
            Long remaining = redis.getExpire(key, TimeUnit.SECONDS);
            // A key that expired between the two calls still counts as a wait of one second
            return remaining == null || remaining <= 0 ? 1 : remaining;
        } catch (DataAccessException ex) {
            log.warn("Could not check the query limit of YOS {}: {}", yosCode, ex.getMessage());
            return 0;
        }
    }

    @Override
    public void release(String yosCode) {
        try {
            redis.delete(PREFIX + yosCode);
        } catch (DataAccessException ex) {
            log.warn("Could not release the query limit of YOS {}: {}", yosCode, ex.getMessage());
        }
    }
}
