package dev.obe.dispatcher.infrastructure.batch;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
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
 * Moves finished deliveries past their retention to the archive table. Each chunk copies and removes
 * its rows in one transaction, so a failure never leaves a row in both tables or in neither.
 */
@Configuration
class DeliveryArchiveJobConfig {

    static final String JOB_NAME = "deliveryArchiveJob";

    private static final String FIND_FINISHED = """
            SELECT event_no FROM event_delivery
            WHERE status <> 'PENDING' AND finished_at < ?
            """;

    private static final String COPY_TO_ARCHIVE = """
            INSERT INTO event_delivery_archive (event_no, yos_code, event_type, resource_type, resource_no,
                occurred_at, status, failed_attempts, last_error, finished_at, correlation_id, archived_at)
            SELECT event_no, yos_code, event_type, resource_type, resource_no,
                occurred_at, status, failed_attempts, last_error, finished_at, correlation_id,
                SYS_EXTRACT_UTC(SYSTIMESTAMP)
            FROM event_delivery WHERE event_no = ?
            """;

    private static final String REMOVE_ARCHIVED = "DELETE FROM event_delivery WHERE event_no = ?";

    @Bean
    Job deliveryArchiveJob(JobRepository repository, Step deliveryArchiveStep) {
        return new JobBuilder(JOB_NAME, repository).start(deliveryArchiveStep).build();
    }

    @Bean
    Step deliveryArchiveStep(JobRepository repository, PlatformTransactionManager transactions,
            JdbcCursorItemReader<String> finishedDeliveryReader, ItemWriter<String> deliveryArchiveWriter,
            @Value("${obe.batch.archive.chunk-size}") int chunkSize) {
        return new StepBuilder("deliveryArchiveStep", repository)
                .<String, String>chunk(chunkSize)
                .transactionManager(transactions)
                .reader(finishedDeliveryReader)
                .writer(deliveryArchiveWriter)
                .build();
    }

    /**
     * Runs once per job with the cutoff of that run. State is not saved: rows of finished chunks are
     * gone from the query, so a restart that skipped that many rows would miss unprocessed ones.
     */
    @Bean
    @StepScope
    JdbcCursorItemReader<String> finishedDeliveryReader(DataSource dataSource,
            @Value("#{jobParameters['" + BatchJobRunner.CUTOFF + "']}") String cutoff) {
        LocalDateTime cutoffUtc = LocalDateTime.ofInstant(Instant.parse(cutoff), ZoneOffset.UTC);
        return new JdbcCursorItemReaderBuilder<String>()
                .name("finishedDeliveryReader")
                .dataSource(dataSource)
                .sql(FIND_FINISHED)
                .preparedStatementSetter((PreparedStatement statement) -> statement.setObject(1, cutoffUtc))
                .rowMapper((row, number) -> row.getString("event_no"))
                .saveState(false)
                .build();
    }

    @Bean
    ItemWriter<String> deliveryArchiveWriter(JdbcTemplate jdbc) {
        return chunk -> {
            jdbc.batchUpdate(COPY_TO_ARCHIVE, chunk.getItems(), chunk.size(),
                    (statement, eventNo) -> statement.setString(1, eventNo));
            jdbc.batchUpdate(REMOVE_ARCHIVED, chunk.getItems(), chunk.size(),
                    (statement, eventNo) -> statement.setString(1, eventNo));
        };
    }
}
