package dev.obe.dispatcher.infrastructure.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionCacheKeys;
import dev.obe.contract.subscription.SubscriptionView;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class CachingSubscriptionClientTest {

    private static final String YOS = "8001";
    private static final String KEY = SubscriptionCacheKeys.of(YOS);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration NOT_SUBSCRIBED_TTL = Duration.ofSeconds(30);
    private static final SubscriptionView VIEW = new SubscriptionView(YOS,
            List.of(new SubscribedType(EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI)));
    private static final String VIEW_JSON = JsonMapper.builder().build().writeValueAsString(VIEW);

    private final FeignSubscriptionClient delegate = mock(FeignSubscriptionClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final CachingSubscriptionClient client = new CachingSubscriptionClient(delegate, redis,
            JsonMapper.builder().build(), TTL, NOT_SUBSCRIBED_TTL);

    @BeforeEach
    void connectMocks() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void returnsCachedSubscriptionWithoutRemoteCall() {
        when(values.get(KEY)).thenReturn(VIEW_JSON);

        assertThat(client.find(YOS)).contains(VIEW);
        verify(delegate, never()).find(anyString());
    }

    @Test
    void remembersRemoteAnswer() {
        when(delegate.find(YOS)).thenReturn(Optional.of(VIEW));

        assertThat(client.find(YOS)).contains(VIEW);
        verify(values).set(KEY, VIEW_JSON, TTL);
    }

    @Test
    void remembersThatYosHasNoSubscriptionForAShorterTime() {
        when(delegate.find(YOS)).thenReturn(Optional.empty());

        assertThat(client.find(YOS)).isEmpty();
        verify(values).set(KEY, "none", NOT_SUBSCRIBED_TTL);
    }

    @Test
    void answersNoSubscriptionFromCache() {
        when(values.get(KEY)).thenReturn("none");

        assertThat(client.find(YOS)).isEmpty();
        verify(delegate, never()).find(anyString());
    }

    @Test
    void asksTheServiceWhenRedisCannotBeRead() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(delegate.find(YOS)).thenReturn(Optional.of(VIEW));

        assertThat(client.find(YOS)).contains(VIEW);
    }

    @Test
    void stillAnswersWhenRedisCannotBeWritten() {
        when(delegate.find(YOS)).thenReturn(Optional.of(VIEW));
        doThrow(new RedisConnectionFailureException("down")).when(values).set(eq(KEY), any(), any(Duration.class));

        assertThat(client.find(YOS)).contains(VIEW);
    }
}
