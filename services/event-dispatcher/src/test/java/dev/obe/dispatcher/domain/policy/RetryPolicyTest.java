package dev.obe.dispatcher.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final RetryPolicy PAYMENT = RetryPolicy.of(EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI);
    private static final Duration ROUNDING = Duration.ofMillis(1);

    @Test
    void mostEventsRetryThreeTimesInThirtyMinutes() {
        assertThat(PAYMENT).isEqualTo(new RetryPolicy(3, Duration.ofMinutes(30)));
    }

    @Test
    void separateAuthEventsRetryThreeTimesInOneMinute() {
        RetryPolicy expected = new RetryPolicy(3, Duration.ofMinutes(1));

        assertThat(RetryPolicy.of(EventType.AYRIK_GKD_BASARILI, ResourceType.ODEME_EMRI_RIZASI)).isEqualTo(expected);
        assertThat(RetryPolicy.of(EventType.AYRIK_GKD_BASARISIZ, ResourceType.HESAP_BILGISI_RIZASI))
                .isEqualTo(expected);
    }

    @Test
    void cardEventsRetryThreeTimesInOneMinute() {
        assertThat(RetryPolicy.of(EventType.KAYNAK_GUNCELLENDI, ResourceType.KART_BILGILERI))
                .isEqualTo(new RetryPolicy(3, Duration.ofMinutes(1)));
    }

    @Test
    void balanceEventsAreNeverRetried() {
        RetryPolicy balance = RetryPolicy.of(EventType.KAYNAK_GUNCELLENDI, ResourceType.BAKIYE);

        assertThat(balance.allowsRetryAfter(1)).isFalse();
    }

    @Test
    void waitDoublesAfterEachFailure() {
        assertThat(PAYMENT.delayAfter(2)).isCloseTo(PAYMENT.delayAfter(1).multipliedBy(2), ROUNDING);
        assertThat(PAYMENT.delayAfter(3)).isCloseTo(PAYMENT.delayAfter(2).multipliedBy(2), ROUNDING);
    }

    @Test
    void allWaitsAddUpToTheWindow() {
        Duration total = PAYMENT.delayAfter(1).plus(PAYMENT.delayAfter(2)).plus(PAYMENT.delayAfter(3));

        assertThat(total).isCloseTo(Duration.ofMinutes(30), ROUNDING);
    }

    @Test
    void allowsExactlyThreeRetries() {
        assertThat(PAYMENT.allowsRetryAfter(3)).isTrue();
        assertThat(PAYMENT.allowsRetryAfter(4)).isFalse();
    }
}
