package dev.obe.subscription.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisUndeliveredQueryLimiterTest {

    private static final String YOS = "8001";
    private static final String KEY = "obe:undelivered-limit:8001";
    private static final Duration INTERVAL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final RedisUndeliveredQueryLimiter limiter = new RedisUndeliveredQueryLimiter(redis, INTERVAL);

    @BeforeEach
    void connectMocks() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void grantsWhenTheKeyCanBeSet() {
        when(values.setIfAbsent(KEY, "1", INTERVAL)).thenReturn(true);

        assertThat(limiter.acquire(YOS)).isZero();
    }

    @Test
    void reportsRemainingSecondsWhenTheKeyExists() {
        when(values.setIfAbsent(KEY, "1", INTERVAL)).thenReturn(false);
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(420L);

        assertThat(limiter.acquire(YOS)).isEqualTo(420);
    }

    @Test
    void neverAnswersZeroWhenTheKeyExpiresBetweenTheTwoCalls() {
        when(values.setIfAbsent(KEY, "1", INTERVAL)).thenReturn(false);
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-2L);

        assertThat(limiter.acquire(YOS)).isEqualTo(1);
    }

    @Test
    void letsTheQueryThroughWhenRedisIsDown() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.acquire(YOS)).isZero();
    }

    @Test
    void releaseRemovesTheKey() {
        limiter.release(YOS);

        verify(redis).delete(eq(KEY));
    }

    @Test
    void releaseSurvivesRedisBeingDown() {
        when(redis.delete(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        limiter.release(YOS);

        verify(redis).delete(KEY);
    }
}
