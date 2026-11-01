package dev.obe.disbursement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreBankingClient.Step;
import dev.obe.disbursement.application.port.out.CoreOutcomeUnknownException;
import dev.obe.disbursement.application.port.out.CoreRefusedException;
import dev.obe.disbursement.application.port.out.CoreUnreachableException;
import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import dev.obe.disbursement.domain.model.DisbursementOutboxEvent;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import dev.obe.disbursement.domain.repository.DisbursementOutboxRepository;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_BAAS_USER=unused", "ORACLE_BAAS_PASSWORD=unused",
        "obe.saga.worker-enabled=false", "obe.outbox.relay-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
@Testcontainers
class DisbursementSagaTest {

    private static final String PARTNER = "PARTNER1";

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private DisbursementSaga saga;

    @Autowired
    private DisbursementService service;

    @Autowired
    private DisbursementRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private DisbursementOutboxRepository outboxRepository;

    @MockitoBean
    private CoreBankingClient core;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    void writesOneEventWhenCompleted() {
        String number = accept(PARTNER, "100.00");

        saga.advanceDue();

        List<DisbursementOutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getDisbursementNo()).isEqualTo(number);
        assertThat(events.get(0).getFinalStatus()).isEqualTo("TAMAMLANDI");
    }

    @Test
    void writesAnEventWhenRefusedByThePreCheck() {
        accept("UNKNOWN", "100.00");

        saga.advanceDue();

        assertThat(outboxRepository.findAll()).extracting(DisbursementOutboxEvent::getFinalStatus)
                .containsExactly("BASARISIZ");
    }

    @Test
    void writesAnEventOnlyAfterCompensationEnds() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("no")).when(core).transfer(anyString());
        doThrow(new CoreUnreachableException("down", null)).doNothing().when(core).releaseLimit(anyString());
        saga.advanceDue();
        assertThat(outboxRepository.count()).isZero();

        makeDue(number);
        saga.advanceDue();

        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void writesNoEventWhileUncertain() {
        accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).transfer(anyString());

        saga.advanceDue();

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void statusStaysWhenItsEventCannotBeWritten() {
        String number = accept(PARTNER, "100.00");
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxRepository).save(any());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.MUHASEBELESTI);
    }

    @Test
    void walksAllStepsInOrder() {
        String number = accept(PARTNER, "1500.50");

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.TAMAMLANDI);
        InOrder order = inOrder(core);
        // compareTo, because equals would treat 1500.5 and 1500.50 as different amounts
        order.verify(core).reserveLimit(eq(number), eq("C-1"),
                argThat(amount -> amount.compareTo(new BigDecimal("1500.50")) == 0));
        order.verify(core).postAccounting(number);
        order.verify(core).transfer(number);
    }

    @Test
    void refusesUnknownPartnerWithoutCallingTheCore() {
        String number = accept("UNKNOWN", "100.00");

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        assertThat(disbursement(number).getFailureReason()).isEqualTo("Partner is not defined");
        verify(core, never()).reserveLimit(anyString(), anyString(), any());
    }

    @Test
    void refusesAmountAboveThePartnerLimit() {
        String number = accept(PARTNER, "50000.01");

        saga.advanceDue();

        assertThat(disbursement(number).getFailureReason()).isEqualTo("Amount is above the partner limit");
        verify(core, never()).reserveLimit(anyString(), anyString(), any());
    }

    @Test
    void stopsWhenTheCreditLimitIsNotEnough() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("no limit")).when(core).reserveLimit(anyString(), anyString(), any());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core, never()).postAccounting(anyString());
        verify(core, never()).transfer(anyString());
    }

    @Test
    void keepsTheLastFinishedStepWhenACallFails() {
        String number = accept(PARTNER, "100.00");
        doThrow(new IllegalStateException("core unreachable")).when(core).postAccounting(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.LIMIT_AYRILDI);
    }

    @Test
    void resumesFromTheLastFinishedStep() {
        String number = accept(PARTNER, "100.00");
        doThrow(new IllegalStateException("core unreachable")).doNothing().when(core).postAccounting(anyString());
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.TAMAMLANDI);
        verify(core, times(1)).reserveLimit(anyString(), anyString(), any());
        verify(core, times(2)).postAccounting(number);
    }

    @Test
    void doesNotTakeARowBeforeItsLeaseEnds() {
        String number = accept(PARTNER, "100.00");
        doThrow(new IllegalStateException("core unreachable")).when(core).postAccounting(anyString());
        saga.advanceDue();

        assertThat(saga.advanceDue()).isZero();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.LIMIT_AYRILDI);
    }

    @Test
    void undoesTheLimitWhenAccountingIsRefused() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("no")).when(core).postAccounting(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core).releaseLimit(number);
        verify(core, never()).reverseAccounting(anyString());
        verify(core, never()).transfer(anyString());
    }

    @Test
    void undoesEverythingNewestFirstWhenTransferIsRefused() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("account closed")).when(core).transfer(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        assertThat(disbursement(number).getFailureReason()).contains("account closed");
        InOrder order = inOrder(core);
        order.verify(core).reverseAccounting(number);
        order.verify(core).releaseLimit(number);
    }

    @Test
    void aLongRefusalMessageDoesNotStopCompensation() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("x".repeat(1000))).when(core).postAccounting(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core).releaseLimit(number);
    }

    @Test
    void finishesCompensationAfterItFailedHalfway() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreRefusedException("no")).when(core).transfer(anyString());
        doThrow(new CoreUnreachableException("down", null)).doNothing().when(core).releaseLimit(anyString());
        saga.advanceDue();
        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.TELAFI_EDILIYOR);

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core, times(2)).reverseAccounting(number);
        verify(core, times(2)).releaseLimit(number);
    }

    @Test
    void unreachableCoreIsRetriedWithoutGoingUncertain() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreUnreachableException("down", null)).when(core).postAccounting(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.LIMIT_AYRILDI);
    }

    @Test
    void unknownOutcomeStopsTheWalk() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).transfer(anyString());

        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BELIRSIZ);
        verify(core, never()).reverseAccounting(anyString());
        verify(core, never()).releaseLimit(anyString());
    }

    @Test
    void unknownOutcomeThatTookEffectCompletes() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).transfer(anyString());
        when(core.isCompleted(Step.TRANSFER, number)).thenReturn(true);
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.TAMAMLANDI);
        verify(core, times(1)).transfer(number);
        verify(core, never()).releaseLimit(anyString());
    }

    @Test
    void unknownOutcomeThatDidNotTakeEffectIsUndone() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).transfer(anyString());
        when(core.isCompleted(Step.TRANSFER, number)).thenReturn(false);
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core).reverseAccounting(number);
        verify(core).releaseLimit(number);
    }

    @Test
    void staysUncertainWhileTheCoreCannotAnswer() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).transfer(anyString());
        when(core.isCompleted(Step.TRANSFER, number)).thenThrow(new CoreUnreachableException("down", null));
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BELIRSIZ);
        verify(core, never()).releaseLimit(anyString());
    }

    @Test
    void asksAboutTheStepThatWasRunning() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core).postAccounting(anyString());
        when(core.isCompleted(Step.ACCOUNTING, number)).thenReturn(true);
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.TAMAMLANDI);
        verify(core, times(1)).postAccounting(number);
        verify(core).transfer(number);
    }

    @Test
    void unknownLimitStepThatDidNotTakeEffectEndsInFailure() {
        String number = accept(PARTNER, "100.00");
        doThrow(new CoreOutcomeUnknownException("timeout", null)).when(core)
                .reserveLimit(anyString(), anyString(), any());
        when(core.isCompleted(Step.LIMIT, number)).thenReturn(false);
        saga.advanceDue();

        makeDue(number);
        saga.advanceDue();

        assertThat(statusOf(number)).isEqualTo(DisbursementStatus.BASARISIZ);
        verify(core, never()).reverseAccounting(anyString());
    }

    @Test
    void leavesFinishedDisbursementsAlone() {
        accept(PARTNER, "100.00");
        saga.advanceDue();

        assertThat(saga.advanceDue()).isZero();
    }

    private void makeDue(String number) {
        jdbc.update("UPDATE disbursement SET next_step_at = SYSTIMESTAMP - 1 WHERE disbursement_no = ?", number);
    }

    private String accept(String partner, String amount) {
        DisbursementDetails details = new DisbursementDetails(partner, "C-1", "TR01", new BigDecimal(amount), "TRY");
        return service.accept(details, UUID.randomUUID().toString()).disbursementNo();
    }

    private DisbursementStatus statusOf(String number) {
        return disbursement(number).getStatus();
    }

    private Disbursement disbursement(String number) {
        return repository.findById(number).orElseThrow();
    }
}
