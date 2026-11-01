package dev.obe.dispatcher.infrastructure.batch;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.PublishedEventsClient;
import dev.obe.observability.Correlation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Compares what the HHS published with what reached this service. An event of a subscribed YOS with
 * no delivery record was lost between Kafka and intake, which nothing else would reveal. Findings are
 * stored and counted, and sending them again stays a decision for a person.
 */
@Configuration
class DeliveryReconciliationJobConfig {

    static final String JOB_NAME = "deliveryReconciliationJob";

    private static final Logger log = LoggerFactory.getLogger(DeliveryReconciliationJobConfig.class);

    private static final String SAVE_FINDING = """
            MERGE INTO reconciliation_finding finding
            USING (SELECT ? event_no FROM dual) source
            ON (finding.event_no = source.event_no)
            WHEN NOT MATCHED THEN INSERT (event_no, yos_code, event_type, resource_type, resource_no,
                occurred_at, correlation_id, found_at)
            VALUES (source.event_no, ?, ?, ?, ?, ?, ?, SYS_EXTRACT_UTC(SYSTIMESTAMP))
            """;

    @Bean
    Job deliveryReconciliationJob(JobRepository repository, Step deliveryReconciliationStep) {
        return new JobBuilder(JOB_NAME, repository).start(deliveryReconciliationStep).build();
    }

    @Bean
    Step deliveryReconciliationStep(JobRepository repository, PlatformTransactionManager transactions,
            PublishedEventReader publishedEventReader,
            ItemProcessor<NotificationEvent, NotificationEvent> missingDeliveryProcessor,
            ItemWriter<NotificationEvent> findingWriter,
            @Value("${obe.batch.reconciliation.chunk-size}") int chunkSize) {
        return new StepBuilder("deliveryReconciliationStep", repository)
                .<NotificationEvent, NotificationEvent>chunk(chunkSize)
                .transactionManager(transactions)
                .reader(publishedEventReader)
                .processor(missingDeliveryProcessor)
                .writer(findingWriter)
                .build();
    }

    @Bean
    @StepScope
    PublishedEventReader publishedEventReader(PublishedEventsClient client,
            @Value("#{jobParameters['" + BatchJobRunner.FROM + "']}") String from,
            @Value("#{jobParameters['" + BatchJobRunner.TO + "']}") String to) {
        return new PublishedEventReader(client, Instant.parse(from), Instant.parse(to));
    }

    @Bean
    ItemWriter<NotificationEvent> findingWriter(JdbcTemplate jdbc, MeterRegistry registry) {
        Counter findings = registry.counter("obe.reconciliation.findings");
        return chunk -> {
            jdbc.batchUpdate(SAVE_FINDING, chunk.getItems(), chunk.size(), (statement, event) -> {
                statement.setString(1, event.eventNo());
                statement.setString(2, event.yosCode());
                statement.setString(3, event.eventType().name());
                statement.setString(4, event.resourceType().name());
                statement.setString(5, event.resourceNo());
                statement.setObject(6, LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
                statement.setString(7, event.correlationId());
            });
            findings.increment(chunk.size());
            chunk.getItems().forEach(event -> Correlation.run(event.correlationId(),
                    () -> log.warn("Event {} for YOS {} has no delivery record", event.eventNo(), event.yosCode())));
        };
    }
}
