package dev.obe.disbursement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.obe.disbursement.domain.exception.InvalidTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DisbursementStatusTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void followsTheHappyPath() {
        Disbursement disbursement = disbursement();

        disbursement.moveTo(DisbursementStatus.ONAYLANDI, NOW);
        disbursement.moveTo(DisbursementStatus.LIMIT_AYRILDI, NOW);
        disbursement.moveTo(DisbursementStatus.MUHASEBELESTI, NOW);
        disbursement.moveTo(DisbursementStatus.TAMAMLANDI, NOW);

        assertThat(disbursement.getStatus()).isEqualTo(DisbursementStatus.TAMAMLANDI);
    }

    @Test
    void cannotSkipAStep() {
        Disbursement disbursement = disbursement();

        assertThatThrownBy(() -> disbursement.moveTo(DisbursementStatus.MUHASEBELESTI, NOW))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(disbursement.getStatus()).isEqualTo(DisbursementStatus.ALINDI);
    }

    @ParameterizedTest
    @EnumSource(DisbursementStatus.class)
    void finalStatesAreFinal(DisbursementStatus next) {
        assertThat(DisbursementStatus.TAMAMLANDI.canMoveTo(next)).isFalse();
        assertThat(DisbursementStatus.BASARISIZ.canMoveTo(next)).isFalse();
    }

    @Test
    void uncertainMovesOnOrIsUndoneButNeverSimplyFails() {
        assertThat(DisbursementStatus.BELIRSIZ.canMoveTo(DisbursementStatus.TAMAMLANDI)).isTrue();
        assertThat(DisbursementStatus.BELIRSIZ.canMoveTo(DisbursementStatus.TELAFI_EDILIYOR)).isTrue();
        assertThat(DisbursementStatus.BELIRSIZ.canMoveTo(DisbursementStatus.BASARISIZ)).isFalse();
    }

    @Test
    void stepsAfterTheLimitMustBeUndoneRatherThanSimplyFail() {
        assertThat(DisbursementStatus.LIMIT_AYRILDI.canMoveTo(DisbursementStatus.BASARISIZ)).isFalse();
        assertThat(DisbursementStatus.MUHASEBELESTI.canMoveTo(DisbursementStatus.BASARISIZ)).isFalse();
    }

    @Test
    void compensationCanOnlyEndInFailure() {
        assertThat(DisbursementStatus.TELAFI_EDILIYOR.canMoveTo(DisbursementStatus.BASARISIZ)).isTrue();
        assertThat(DisbursementStatus.TELAFI_EDILIYOR.canMoveTo(DisbursementStatus.TAMAMLANDI)).isFalse();
    }

    @Test
    void remembersWhichStepIsUncertain() {
        Disbursement disbursement = disbursement();
        disbursement.moveTo(DisbursementStatus.ONAYLANDI, NOW);

        disbursement.markUncertain(NOW);

        assertThat(disbursement.getUncertainFrom()).isEqualTo(DisbursementStatus.ONAYLANDI);
    }

    @Test
    void remembersThatAccountingWasPosted() {
        Disbursement disbursement = disbursement();
        disbursement.moveTo(DisbursementStatus.ONAYLANDI, NOW);
        disbursement.moveTo(DisbursementStatus.LIMIT_AYRILDI, NOW);
        assertThat(disbursement.isAccountingPosted()).isFalse();

        disbursement.moveTo(DisbursementStatus.MUHASEBELESTI, NOW);

        assertThat(disbursement.isAccountingPosted()).isTrue();
    }

    @Test
    void uncertainCannotHappenBeforeAnyExternalCall() {
        assertThat(DisbursementStatus.ALINDI.canMoveTo(DisbursementStatus.BELIRSIZ)).isFalse();
    }

    @Test
    void failureKeepsItsReason() {
        Disbursement disbursement = disbursement();

        disbursement.fail("Limit is not enough", NOW);

        assertThat(disbursement.getStatus()).isEqualTo(DisbursementStatus.BASARISIZ);
        assertThat(disbursement.getFailureReason()).isEqualTo("Limit is not enough");
    }

    @Test
    void sameRequestHasTheSameHashWhateverTheAmountScale() {
        assertThat(details("100").hash()).isEqualTo(details("100.00").hash());
        assertThat(details("100").hash()).isNotEqualTo(details("100.01").hash());
    }

    private Disbursement disbursement() {
        return Disbursement.create(details("100.00"), "key-1", "hash", null, NOW);
    }

    private DisbursementDetails details(String amount) {
        return new DisbursementDetails("PARTNER1", "C-1", "TR00", new BigDecimal(amount), "TRY");
    }
}
