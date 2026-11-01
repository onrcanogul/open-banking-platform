package dev.obe.disbursement.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreBankingClient.Step;
import dev.obe.disbursement.application.port.out.CoreUnreachableException;
import dev.obe.disbursement.application.service.DisbursementService;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_BAAS_USER=unused", "ORACLE_BAAS_PASSWORD=unused",
        "obe.saga.worker-enabled=false", "obe.outbox.relay-enabled=false", "obe.reconciliation.scheduler-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
@Testcontainers
class ReconciliationJobTest {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private ReconciliationRunner runner;

    @Autowired
    private DisbursementService service;

    @Autowired
    private DisbursementRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CoreBankingClient core;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.update("DELETE FROM disbursement_mismatch");
    }

    @Test
    void reportsMoneyThatMovedForAFailedDisbursement() {
        String number = disbursement("BASARISIZ", Duration.ofMinutes(5));
        transferred(number, true);

        assertThat(runner.run(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(mismatchTypes(number)).containsExactly("PAID_BUT_FAILED");
    }

    @Test
    void reportsACompletedDisbursementWithoutATransfer() {
        String number = disbursement("TAMAMLANDI", Duration.ofMinutes(5));
        transferred(number, false);

        runner.run(true);

        assertThat(mismatchTypes(number)).containsExactly("COMPLETED_BUT_UNPAID");
    }

    @Test
    void reportsNothingWhenBothSidesAgree() {
        String completed = disbursement("TAMAMLANDI", Duration.ofMinutes(5));
        String failed = disbursement("BASARISIZ", Duration.ofMinutes(5));
        transferred(completed, true);
        transferred(failed, false);

        runner.run(true);

        assertThat(mismatchTypes(completed)).isEmpty();
        assertThat(mismatchTypes(failed)).isEmpty();
    }

    @Test
    void reportsAnOldUncertainDisbursement() {
        String number = disbursement("BELIRSIZ", Duration.ofHours(2));
        transferred(number, true);

        runner.run(true);

        assertThat(mismatchTypes(number)).containsExactly("STALE_UNCERTAIN");
    }

    @Test
    void leavesAFreshUncertainDisbursementToTheSaga() {
        String number = disbursement("BELIRSIZ", Duration.ofMinutes(5));

        runner.run(true);

        assertThat(mismatchTypes(number)).isEmpty();
    }

    @Test
    void doesNotLookBeyondThePeriod() {
        String old = disbursement("BASARISIZ", Duration.ofDays(5));
        transferred(old, true);

        runner.run(true);

        assertThat(mismatchTypes(old)).isEmpty();
    }

    @Test
    void reportsOneCaseOnce() {
        String number = disbursement("BASARISIZ", Duration.ofMinutes(5));
        transferred(number, true);

        runner.run(true);
        assertThat(runner.run(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(mismatchTypes(number)).hasSize(1);
    }

    @Test
    void failsVisiblyInsteadOfReportingAnUncheckedRowAsClean() {
        String number = disbursement("TAMAMLANDI", Duration.ofMinutes(5));
        when(core.isCompleted(any(), eq(number))).thenThrow(new CoreUnreachableException("down", null));

        assertThat(runner.run(true)).isEqualTo(BatchStatus.FAILED);
    }

    private void transferred(String number, boolean completed) {
        when(core.isCompleted(Step.TRANSFER, number)).thenReturn(completed);
    }

    private String disbursement(String status, Duration age) {
        DisbursementDetails details = new DisbursementDetails("PARTNER1", "C-1", "TR01", new BigDecimal("100.00"),
                "TRY");
        String number = service.accept(details, UUID.randomUUID().toString()).disbursementNo();
        jdbc.update("UPDATE disbursement SET status = ?, updated_at = SYS_EXTRACT_UTC(SYSTIMESTAMP) - ? "
                + "WHERE disbursement_no = ?", status, age.toMinutes() / 1440.0, number);
        return number;
    }

    private List<String> mismatchTypes(String number) {
        return jdbc.queryForList("SELECT mismatch_type FROM disbursement_mismatch WHERE disbursement_no = ?",
                String.class, number);
    }
}
