package dev.obe.disbursement.infrastructure.batch;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Starts the job. The date identifies a scheduled run, so a second instance of this service cannot
 * start the same run. A manual run adds its time and always starts.
 */
@Component
public class ReconciliationRunner {

    static final String FROM = "from";
    static final String UNCERTAIN_BEFORE = "uncertainBefore";

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRunner.class);
    private static final ZoneId BANK_ZONE = ZoneId.of("Europe/Istanbul");

    private final JobOperator jobOperator;
    private final Job disbursementReconciliationJob;
    private final Duration lookBack;
    private final Duration uncertainAge;

    ReconciliationRunner(JobOperator jobOperator, Job disbursementReconciliationJob,
            @Value("${obe.reconciliation.look-back}") Duration lookBack,
            @Value("${obe.reconciliation.uncertain-age}") Duration uncertainAge) {
        this.jobOperator = jobOperator;
        this.disbursementReconciliationJob = disbursementReconciliationJob;
        this.lookBack = lookBack;
        this.uncertainAge = uncertainAge;
    }

    public BatchStatus run(boolean manual) {
        Instant now = Instant.now();
        JobParametersBuilder parameters = new JobParametersBuilder()
                .addString("runDate", LocalDate.now(BANK_ZONE).toString())
                .addString(FROM, now.minus(lookBack).toString(), false)
                .addString(UNCERTAIN_BEFORE, now.minus(uncertainAge).toString(), false);
        if (manual) {
            parameters.addString("manualRun", now.toString());
        }
        return start(parameters.toJobParameters());
    }

    private BatchStatus start(JobParameters parameters) {
        try {
            JobExecution execution = jobOperator.start(disbursementReconciliationJob, parameters);
            log.info("Reconciliation ended with {}", execution.getStatus());
            return execution.getStatus();
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException ex) {
            log.info("Reconciliation is skipped because it already ran for this date");
            return BatchStatus.ABANDONED;
        } catch (Exception ex) {
            log.error("Reconciliation could not start", ex);
            return BatchStatus.FAILED;
        }
    }
}
