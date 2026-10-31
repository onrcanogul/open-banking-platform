package dev.obe.mockhhs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.jayway.jsonpath.JsonPath;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.mockhhs.application.port.out.EventPublishException;
import dev.obe.mockhhs.application.port.out.EventPublisher;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.model.OutboxStatus;
import dev.obe.mockhhs.domain.model.PaymentOrder;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import dev.obe.mockhhs.domain.repository.PaymentOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_HHS_USER=unused", "ORACLE_HHS_PASSWORD=unused",
        "obe.outbox.relay-enabled=false", "obe.outbox.batch-size=10"})
@Testcontainers
class OutboxRelayServiceTest {

    private static final Duration AFTER_LAST_MESSAGE = Duration.ofSeconds(2);
    private static final Duration READ_LIMIT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    @Autowired
    private OutboxRelayService relay;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactions;

    @MockitoSpyBean
    private EventPublisher publisher;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEvent event = pendingEvent();

        int published = relay.publishPending();

        assertThat(published).isEqualTo(1);
        List<ConsumerRecord<String, String>> records = readMessages(Set.of(event.getEventNo()));
        assertThat(records).hasSize(1);
        ConsumerRecord<String, String> message = records.get(0);
        assertThat(message.key()).isEqualTo(event.getResourceNo());
        assertThat(JsonPath.<String>read(message.value(), "$.eventType")).isEqualTo("KAYNAK_GUNCELLENDI");
        assertThat(JsonPath.<String>read(message.value(), "$.resourceType")).isEqualTo("ODEME_EMRI");
        assertThat(JsonPath.<String>read(message.value(), "$.yosCode")).isEqualTo("8001");
        assertThat(statusOf(event)).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void doesNotPublishSameEventTwice() {
        OutboxEvent event = pendingEvent();

        relay.publishPending();
        int secondRun = relay.publishPending();

        assertThat(secondRun).isZero();
        assertThat(readMessages(Set.of(event.getEventNo()))).hasSize(1);
    }

    @Test
    void keepsEventPendingWhenBrokerRejectsIt() {
        OutboxEvent event = pendingEvent();
        doThrow(new EventPublishException("broker down", null)).when(publisher).publish(any());

        assertThatThrownBy(() -> relay.publishPending()).isInstanceOf(EventPublishException.class);

        assertThat(statusOf(event)).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void publishesAtMostOneBatchPerCall() {
        IntStream.range(0, 25).forEach(i -> pendingEvent());

        assertThat(relay.publishPending()).isEqualTo(10);
        assertThat(outboxRepository.findAll().stream().filter(e -> e.getStatus() == OutboxStatus.PENDING))
                .hasSize(15);
    }

    @Test
    void concurrentRelaysDoNotPublishSameEventTwice() throws Exception {
        Set<String> eventNos = IntStream.range(0, 40)
                .mapToObj(i -> pendingEvent().getEventNo())
                .collect(Collectors.toSet());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(pool.submit(this::drain));
            }
            for (Future<?> run : runs) {
                run.get();
            }
        } finally {
            pool.shutdown();
        }
        drain();

        List<ConsumerRecord<String, String>> records = readMessages(eventNos);
        assertThat(records).hasSameSizeAs(eventNos);
        assertThat(records.stream().map(record -> eventNoOf(record)).distinct()).hasSameSizeAs(eventNos);
    }

    @Test
    void skipsEventLockedByAnotherRelay() throws Exception {
        OutboxEvent lockedEvent = pendingEvent();
        OutboxEvent freeEvent = pendingEvent();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> transactions.executeWithoutResult(status -> {
                outboxRepository.lockPending(List.of(lockedEvent.getEventNo()));
                locked.countDown();
                awaitUninterruptibly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // A blocking lock would wait for the other transaction and hit this timeout
            int published = pool.submit(relay::publishPending).get(10, TimeUnit.SECONDS);

            assertThat(published).isEqualTo(1);
            assertThat(statusOf(freeEvent)).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(statusOf(lockedEvent)).isEqualTo(OutboxStatus.PENDING);
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

    private void drain() {
        while (relay.publishPending() > 0) {
            Thread.onSpinWait();
        }
    }

    private OutboxEvent pendingEvent() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentOrder order = orderRepository.save(PaymentOrder.create("8001", new BigDecimal("10.00"), now));
        return outboxRepository.save(OutboxEvent.paymentOrderUpdated(order, now));
    }

    private OutboxStatus statusOf(OutboxEvent event) {
        return outboxRepository.findById(event.getEventNo()).orElseThrow().getStatus();
    }

    private String eventNoOf(ConsumerRecord<String, String> record) {
        return JsonPath.read(record.value(), "$.eventNo");
    }

    /** Reads for a while after the last expected message, so that duplicates are seen too. */
    private List<ConsumerRecord<String, String>> readMessages(Set<String> eventNos) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(NotificationTopics.EVENTS));
            Instant readUntil = Instant.now().plus(READ_LIMIT);
            while (Instant.now().isBefore(readUntil)) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (eventNos.contains(eventNoOf(record))) {
                        matches.add(record);
                    }
                });
                if (matches.size() >= eventNos.size()) {
                    readUntil = minimum(readUntil, Instant.now().plus(AFTER_LAST_MESSAGE));
                }
            }
        }
        return matches;
    }

    private Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
