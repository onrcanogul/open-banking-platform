package dev.obe.dispatcher.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.EventListenerClient;
import dev.obe.dispatcher.application.port.out.PublishedEventsClient;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import dev.obe.dispatcher.application.service.DeliveryStore;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
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

@SpringBootTest(properties = {"ORACLE_DISPATCHER_USER=unused", "ORACLE_DISPATCHER_PASSWORD=unused",
        "REDIS_PASSWORD=unused", "obe.delivery.scheduler-enabled=false", "obe.batch.scheduler-enabled=false",
        "spring.kafka.listener.auto-startup=false", "obe.batch.archive.chunk-size=10",
        "obe.batch.reconciliation.chunk-size=10"})
@Testcontainers
class BatchJobsTest {

    private static final String YOS = "8001";
    private static final String UNSUBSCRIBED_YOS = "8002";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private BatchJobRunner runner;

    @Autowired
    private DeliveryStore store;

    @Autowired
    private EventDeliveryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry registry;

    @MockitoBean
    private PublishedEventsClient publishedEvents;

    @MockitoBean
    private SubscriptionClient subscriptions;

    @MockitoBean
    private EventListenerClient listenerClient;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.update("DELETE FROM event_delivery_archive");
        jdbc.update("DELETE FROM reconciliation_finding");
        when(subscriptions.find(YOS)).thenReturn(Optional.of(new SubscriptionView(YOS,
                List.of(new SubscribedType(EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI)))));
        when(subscriptions.find(UNSUBSCRIBED_YOS)).thenReturn(Optional.empty());
    }

    @Test
    void movesOldFinishedDeliveriesToTheArchive() {
        NotificationEvent old = delivered(Duration.ofDays(40));

        assertThat(runner.runArchive(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(repository.findById(old.eventNo())).isEmpty();
        assertThat(archivedStatus(old)).isEqualTo("DELIVERED");
    }

    @Test
    void keepsRecentAndUnfinishedDeliveries() {
        NotificationEvent recent = delivered(Duration.ofDays(1));
        NotificationEvent stillPending = event(YOS);
        store.saveNew(List.of(stillPending));

        runner.runArchive(true);

        assertThat(repository.findById(recent.eventNo())).isPresent();
        assertThat(repository.findById(stillPending.eventNo())).isPresent();
        assertThat(archivedCount()).isZero();
    }

    @Test
    void archivesMoreRowsThanOneChunkHolds() {
        IntStream.range(0, 25).forEach(i -> delivered(Duration.ofDays(40)));

        assertThat(runner.runArchive(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(archivedCount()).isEqualTo(25);
        assertThat(repository.count()).isZero();
    }

    @Test
    void runningArchiveAgainChangesNothing() {
        delivered(Duration.ofDays(40));
        runner.runArchive(true);

        assertThat(runner.runArchive(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(archivedCount()).isEqualTo(1);
    }

    @Test
    void scheduledRunStartsOnlyOncePerDay() {
        assertThat(runner.runArchive(false)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(runner.runArchive(false)).isEqualTo(BatchStatus.ABANDONED);
    }

    @Test
    void reportsPublishedEventWithoutDeliveryRecord() {
        NotificationEvent lost = event(YOS);
        published(lost);

        assertThat(runner.runReconciliation(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(findingCount(lost)).isEqualTo(1);
    }

    @Test
    void ignoresEventThatArrived() {
        NotificationEvent arrived = event(YOS);
        store.saveNew(List.of(arrived));
        published(arrived);

        runner.runReconciliation(true);

        assertThat(findingCount(arrived)).isZero();
    }

    @Test
    void ignoresEventOfYosWithoutSubscription() {
        NotificationEvent notSubscribed = event(UNSUBSCRIBED_YOS);
        published(notSubscribed);

        runner.runReconciliation(true);

        assertThat(findingCount(notSubscribed)).isZero();
    }

    @Test
    void ignoresTypeTheYosDidNotSubscribeTo() {
        NotificationEvent otherType = new NotificationEvent(UUID.randomUUID().toString(), YOS,
                EventType.KAYNAK_GUNCELLENDI, ResourceType.BAKIYE, UUID.randomUUID().toString(), NOW);
        published(otherType);

        runner.runReconciliation(true);

        assertThat(findingCount(otherType)).isZero();
    }

    @Test
    void readsEveryPage() {
        List<NotificationEvent> firstPage = IntStream.range(0, 15).mapToObj(i -> event(YOS)).toList();
        List<NotificationEvent> secondPage = List.of(event(YOS), event(YOS));
        when(publishedEvents.find(any(), any(), anyInt())).thenReturn(List.of());
        when(publishedEvents.find(any(), any(), eq(1))).thenReturn(firstPage);
        when(publishedEvents.find(any(), any(), eq(2))).thenReturn(secondPage);

        runner.runReconciliation(true);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_finding", Integer.class)).isEqualTo(17);
    }

    @Test
    void runningReconciliationAgainReportsNothingTwice() {
        NotificationEvent lost = event(YOS);
        published(lost);

        runner.runReconciliation(true);
        double countedOnce = registry.get("obe.reconciliation.findings").counter().count();
        assertThat(runner.runReconciliation(true)).isEqualTo(BatchStatus.COMPLETED);

        assertThat(findingCount(lost)).isEqualTo(1);
        assertThat(registry.get("obe.reconciliation.findings").counter().count()).isEqualTo(countedOnce);
    }

    @Test
    void failsVisiblyWhenTheHhsCannotBeReached() {
        when(publishedEvents.find(any(), any(), anyInt())).thenThrow(new IllegalStateException("hhs down"));

        assertThat(runner.runReconciliation(true)).isEqualTo(BatchStatus.FAILED);
    }

    private void published(NotificationEvent event) {
        when(publishedEvents.find(any(), any(), anyInt())).thenReturn(List.of());
        when(publishedEvents.find(any(), any(), eq(1))).thenReturn(List.of(event));
    }

    private NotificationEvent delivered(Duration age) {
        NotificationEvent event = event(YOS);
        store.saveNew(List.of(event));
        store.markDelivered(List.of(event.eventNo()));
        jdbc.update("UPDATE event_delivery SET finished_at = SYS_EXTRACT_UTC(SYSTIMESTAMP) - ? WHERE event_no = ?",
                age.toDays(), event.eventNo());
        return event;
    }

    private NotificationEvent event(String yosCode) {
        return new NotificationEvent(UUID.randomUUID().toString(), yosCode, EventType.KAYNAK_GUNCELLENDI,
                ResourceType.ODEME_EMRI, UUID.randomUUID().toString(), NOW);
    }

    private String archivedStatus(NotificationEvent event) {
        return jdbc.queryForObject("SELECT status FROM event_delivery_archive WHERE event_no = ?", String.class,
                event.eventNo());
    }

    private int archivedCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_delivery_archive", Integer.class);
    }

    private int findingCount(NotificationEvent event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_finding WHERE event_no = ?", Integer.class,
                event.eventNo());
    }
}
