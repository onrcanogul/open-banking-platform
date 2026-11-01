package dev.obe.dispatcher.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_DISPATCHER_USER=unused", "ORACLE_DISPATCHER_PASSWORD=unused",
        "REDIS_PASSWORD=unused", "obe.delivery.scheduler-enabled=false", "spring.kafka.listener.auto-startup=false",
        "obe.delivery.claim-size=10", "obe.delivery.claim-lease=PT2S"})
@Testcontainers
class DeliveryStoreTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private DeliveryStore store;

    @Autowired
    private EventDeliveryRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void claimedDeliveryIsNotClaimedAgainDuringItsLease() {
        store.saveNew(List.of(event()));

        assertThat(store.claimDue()).hasSize(1);
        assertThat(store.claimDue()).isEmpty();
    }

    @Test
    void deliveryReturnsToQueueWhenTheLeaseEnds() throws Exception {
        store.saveNew(List.of(event()));

        assertThat(store.claimDue()).hasSize(1);
        Thread.sleep(Duration.ofSeconds(3).toMillis());

        assertThat(store.claimDue()).hasSize(1);
    }

    @Test
    void claimsAtMostOneBatch() {
        store.saveNew(IntStream.range(0, 25).mapToObj(i -> event()).toList());

        assertThat(store.claimDue()).hasSize(10);
    }

    @Test
    void concurrentSendersClaimDifferentDeliveries() throws Exception {
        store.saveNew(IntStream.range(0, 8).mapToObj(i -> event()).toList());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<List<NotificationEvent>>> claims = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                claims.add(pool.submit(store::claimDue));
            }
            List<String> claimed = new ArrayList<>();
            for (Future<List<NotificationEvent>> claim : claims) {
                claim.get().forEach(event -> claimed.add(event.eventNo()));
            }
            assertThat(claimed).doesNotHaveDuplicates();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void skipsDeliveryLockedByAnotherSender() throws Exception {
        NotificationEvent lockedEvent = event();
        NotificationEvent freeEvent = event();
        store.saveNew(List.of(lockedEvent, freeEvent));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> transactions.executeWithoutResult(status -> {
                repository.lockDue(List.of(lockedEvent.eventNo()), Instant.now());
                locked.countDown();
                awaitUninterruptibly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // A blocking lock would wait for the other transaction and hit this timeout
            List<NotificationEvent> claimed = pool.submit(store::claimDue).get(10, TimeUnit.SECONDS);

            assertThat(claimed).extracting(NotificationEvent::eventNo).containsExactly(freeEvent.eventNo());
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    @Test
    void listsUndeliveredEventsOfOneYosOldestFirst() {
        NotificationEvent newer = balanceEvent("8001", NOW.minusSeconds(10));
        NotificationEvent older = balanceEvent("8001", NOW.minusSeconds(20));
        NotificationEvent otherYos = balanceEvent("8002", NOW.minusSeconds(15));
        makeUndelivered(newer, older, otherYos);

        assertThat(store.findUndelivered("8001", NOW.minusSeconds(60), NOW, 1))
                .extracting(NotificationEvent::eventNo)
                .containsExactly(older.eventNo(), newer.eventNo());
    }

    @Test
    void leavesOutEventsOutsideTheTimeRange() {
        NotificationEvent tooOld = balanceEvent("8001", NOW.minusSeconds(100));
        NotificationEvent inRange = balanceEvent("8001", NOW.minusSeconds(10));
        makeUndelivered(tooOld, inRange);

        assertThat(store.findUndelivered("8001", NOW.minusSeconds(60), NOW, 1))
                .extracting(NotificationEvent::eventNo)
                .containsExactly(inRange.eventNo());
    }

    @Test
    void leavesOutEventsStillBeingRetried() {
        store.saveNew(List.of(balanceEvent("8001", NOW.minusSeconds(10))));

        assertThat(store.findUndelivered("8001", NOW.minusSeconds(60), NOW, 1)).isEmpty();
    }

    @Test
    void splitsUndeliveredEventsIntoPagesOfOneHundred() {
        NotificationEvent[] events = IntStream.range(0, 120)
                .mapToObj(i -> balanceEvent("8001", NOW.minusSeconds(200 - i)))
                .toArray(NotificationEvent[]::new);
        makeUndelivered(events);

        assertThat(store.findUndelivered("8001", NOW.minusSeconds(300), NOW, 1)).hasSize(100);
        assertThat(store.findUndelivered("8001", NOW.minusSeconds(300), NOW, 2)).hasSize(20);
    }

    /** Balance events have no retries, so one failure is enough. */
    private void makeUndelivered(NotificationEvent... events) {
        List<NotificationEvent> list = List.of(events);
        store.saveNew(list);
        store.recordFailure(list.stream().map(NotificationEvent::eventNo).toList(), "503");
    }

    private NotificationEvent balanceEvent(String yosCode, Instant occurredAt) {
        return new NotificationEvent(UUID.randomUUID().toString(), yosCode, EventType.KAYNAK_GUNCELLENDI,
                ResourceType.BAKIYE, UUID.randomUUID().toString(), occurredAt);
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private NotificationEvent event() {
        return new NotificationEvent(UUID.randomUUID().toString(), "8001", EventType.KAYNAK_GUNCELLENDI,
                ResourceType.ODEME_EMRI, UUID.randomUUID().toString(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
