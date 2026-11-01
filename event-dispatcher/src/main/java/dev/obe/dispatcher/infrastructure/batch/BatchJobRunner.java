package dev.obe.dispatcher.infrastructure.batch;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * Starts jobs. The date is the identifying parameter of a scheduled run, so a second instance of this
 * service starting the same job on the same day is refused by Spring Batch. A manual run adds its
 * time and always starts. The other values describe the run and do not identify it.
 */
@Component
public class BatchJobRunner {

    static final String CUTOFF = "cutoff";
    static final String FROM = "from";
    static final String TO = "to";

    private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);
    private static final ZoneId BANK_ZONE = ZoneId.of("Europe/Istanbul");
    private static final String RUN_DATE = "runDate";
    private static final String MANUAL_RUN = "manualRun";

    private final JobOperator jobOperator;
    private final Job deliveryArchiveJob;
    private final Job deliveryReconciliationJob;
    private final Duration retention;
    private final Duration settleTime;

    BatchJobRunner(JobOperator jobOperator, Job deliveryArchiveJob, Job deliveryReconciliationJob,
            @Value("${obe.batch.archive.retention}") Duration retention,
            @Value("${obe.batch.reconciliation.settle-time}") Duration settleTime) {
        this.jobOperator = jobOperator;
        this.deliveryArchiveJob = deliveryArchiveJob;
        this.deliveryReconciliationJob = deliveryReconciliationJob;
        this.retention = retention;
        this.settleTime = settleTime;
    }

    public BatchStatus runArchive(boolean manual) {
        return run(deliveryArchiveJob, parameters(manual)
                .addString(CUTOFF, now().minus(retention).toString(), false)
                .toJobParameters());
    }

    /** Checks the previous day and today, up to events that have had time to arrive. */
    public BatchStatus runReconciliation(boolean manual) {
        Instant from = today().minusDays(1).atStartOfDay(BANK_ZONE).toInstant();
        return run(deliveryReconciliationJob, parameters(manual)
                .addString(FROM, from.toString(), false)
                .addString(TO, now().minus(settleTime).toString(), false)
                .toJobParameters());
    }

    private JobParametersBuilder parameters(boolean manual) {
        JobParametersBuilder builder = new JobParametersBuilder().addString(RUN_DATE, today().toString());
        return manual ? builder.addString(MANUAL_RUN, now().toString()) : builder;
    }

    private BatchStatus run(Job job, JobParameters parameters) {
        try {
            JobExecution execution = jobOperator.start(job, parameters);
            log.info("Job {} ended with {}", job.getName(), execution.getStatus());
            return execution.getStatus();
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException ex) {
            log.info("Job {} is skipped because it already ran for this date", job.getName());
            return BatchStatus.ABANDONED;
        } catch (Exception ex) {
            log.error("Job {} could not start", job.getName(), ex);
            return BatchStatus.FAILED;
        }
    }

    private LocalDate today() {
        return LocalDate.now(BANK_ZONE);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
