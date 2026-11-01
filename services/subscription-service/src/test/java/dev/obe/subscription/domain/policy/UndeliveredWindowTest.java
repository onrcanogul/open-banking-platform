package dev.obe.subscription.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class UndeliveredWindowTest {

    private static final Instant QUERY_TIME = at("2024-01-09T10:15:00+03:00");
    private static final Instant START_OF_PREVIOUS_DAY = at("2024-01-08T00:00:00+03:00");

    @Test
    void limitsAWiderRangeAsInTheStandardsExample() {
        UndeliveredWindow window = UndeliveredWindow.of(QUERY_TIME, at("2024-01-07T10:00:00+03:00"),
                at("2024-01-09T16:00:00+03:00"));

        assertThat(window).isEqualTo(new UndeliveredWindow(START_OF_PREVIOUS_DAY, QUERY_TIME));
    }

    @Test
    void usesFullRangeWhenNothingIsRequested() {
        assertThat(UndeliveredWindow.of(QUERY_TIME, null, null))
                .isEqualTo(new UndeliveredWindow(START_OF_PREVIOUS_DAY, QUERY_TIME));
    }

    @Test
    void keepsANarrowerRange() {
        Instant from = at("2024-01-08T12:00:00+03:00");
        Instant to = at("2024-01-09T09:00:00+03:00");

        assertThat(UndeliveredWindow.of(QUERY_TIME, from, to)).isEqualTo(new UndeliveredWindow(from, to));
    }

    @Test
    void countsDaysInTheBanksTimeZone() {
        // 22:30 UTC on the 8th is already the 9th in Istanbul
        Instant now = at("2024-01-08T22:30:00Z");

        assertThat(UndeliveredWindow.of(now, null, null).from()).isEqualTo(START_OF_PREVIOUS_DAY);
    }

    private static Instant at(String time) {
        return OffsetDateTime.parse(time).toInstant();
    }
}
