package dev.obe.disbursement.infrastructure.batch;

import dev.obe.observability.Correlation;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Checks ended disbursements against the core at the end of the day. It only reports: changing money
 * or telling a partner something new after the fact is a decision for a person.
 */
@Configuration
class ReconciliationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobConfig.class);

    private static final String FIND_TO_CHECK = """
            SELECT disbursement_no, status, correlation_id FROM disbursement
            WHERE (status IN ('TAMAMLANDI', 'BASARISIZ') AND updated_at >= ?)
               OR (status = 'BELIRSIZ' AND updated_at < ?)
            """;

    private static final String SAVE_MISMATCH = """
            MERGE INTO disbursement_mismatch found
            USING (SELECT ? disbursement_no FROM dual) source
            ON (found.disbursement_no = source.disbursement_no)
            WHEN NOT MATCHED THEN INSERT (disbursement_no, mismatch_type, our_status, core_transfer,
                correlation_id, found_at)
            VALUES (source.disbursement_no, ?, ?, ?, ?, SYS_EXTRACT_UTC(SYSTIMESTAMP))
            """;

    @Bean
    Job disbursementReconciliationJob(JobRepository repository, Step disbursementReconciliationStep) {
        return new JobBuilder("disbursementReconciliationJob", repository)
                .start(disbursementReconciliationStep)
                .build();
    }

    @Bean
    Step disbursementReconciliationStep(JobRepository repository, PlatformTransactionManager transactions,
            JdbcCursorItemReader<DisbursementToCheck> disbursementToCheckReader, MismatchProcessor processor,
            ItemWriter<Mismatch> mismatchWriter, @Value("${obe.reconciliation.chunk-size}") int chunkSize) {
        return new StepBuilder("disbursementReconciliationStep", repository)
                .<DisbursementToCheck, Mismatch>chunk(chunkSize)
                .transactionManager(transactions)
                .reader(disbursementToCheckReader)
                .processor(processor)
                .writer(mismatchWriter)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<DisbursementToCheck> disbursementToCheckReader(DataSource dataSource,
            @Value("#{jobParameters['" + ReconciliationRunner.FROM + "']}") String from,
            @Value("#{jobParameters['" + ReconciliationRunner.UNCERTAIN_BEFORE + "']}") String uncertainBefore) {
        return new JdbcCursorItemReaderBuilder<DisbursementToCheck>()
                .name("disbursementToCheckReader")
                .dataSource(dataSource)
                .sql(FIND_TO_CHECK)
                .preparedStatementSetter((PreparedStatement statement) -> {
                    statement.setObject(1, utc(from));
                    statement.setObject(2, utc(uncertainBefore));
                })
                .rowMapper((row, number) -> new DisbursementToCheck(row.getString("disbursement_no"),
                        row.getString("status"), row.getString("correlation_id")))
                .build();
    }

    @Bean
    ItemWriter<Mismatch> mismatchWriter(JdbcTemplate jdbc, MeterRegistry registry) {
        return chunk -> {
            jdbc.batchUpdate(SAVE_MISMATCH, chunk.getItems(), chunk.size(), (statement, mismatch) -> {
                statement.setString(1, mismatch.disbursementNo());
                statement.setString(2, mismatch.type().name());
                statement.setString(3, mismatch.ourStatus());
                statement.setInt(4, mismatch.coreTransfer() ? 1 : 0);
                statement.setString(5, mismatch.correlationId());
            });
            chunk.getItems().forEach(mismatch -> {
                registry.counter("obe.disbursement.mismatches", "type", mismatch.type().name()).increment();
                Correlation.run(mismatch.correlationId(), () -> log.error("Disbursement {} needs review: {}",
                        mismatch.disbursementNo(), mismatch.type()));
            });
        };
    }

    private LocalDateTime utc(String instant) {
        return LocalDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }
}
