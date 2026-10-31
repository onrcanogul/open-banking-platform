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
        "obe.delivery.scheduler-enabled=false", "spring.kafka.listener.auto-startup=false",
        "obe.delivery.claim-size=10", "obe.delivery.claim-lease=PT2S"})
@Testcontainers
class DeliveryStoreTest {

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
