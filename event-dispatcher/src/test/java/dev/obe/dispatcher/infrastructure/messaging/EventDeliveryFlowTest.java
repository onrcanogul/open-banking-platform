package dev.obe.dispatcher.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.contract.event.ResourceType;
import dev.obe.contract.subscription.SubscriptionCacheKeys;
import dev.obe.dispatcher.domain.model.DeliveryStatus;
import dev.obe.dispatcher.domain.model.EventDelivery;
import dev.obe.dispatcher.domain.repository.EventDeliveryRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

/** Runs the dispatcher against a real broker and database, with HTTP stubs for other services. */
@SpringBootTest(properties = {"ORACLE_DISPATCHER_USER=unused", "ORACLE_DISPATCHER_PASSWORD=unused",
        "REDIS_PASSWORD=unused", "obe.redelivery-interval=PT0.5S", "obe.delivery.poll-interval=PT0.2S",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"})
@Testcontainers
class EventDeliveryFlowTest {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(30);
    private static final Duration OUTAGE = Duration.ofSeconds(3);

    private static final String YOS_A = "8001";
    private static final String YOS_B = "8002";
    private static final String UNSUBSCRIBED_YOS = "8003";

    private static final String TYPES = """
            [{"eventType":"KAYNAK_GUNCELLENDI","resourceType":"ODEME_EMRI"},
             {"eventType":"KAYNAK_GUNCELLENDI","resourceType":"BAKIYE"}]""";

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8.10.3-alpine").withExposedPorts(6379);

    private static final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private static final AtomicInteger subscriptionLookups = new AtomicInteger();
    private static final Set<String> downYoses = ConcurrentHashMap.newKeySet();
    private static volatile boolean subscriptionServiceDown;
    private static HttpServer stubs;

    @Autowired
    private KafkaTemplate<String, NotificationEvent> producer;

    @Autowired
    private EventDeliveryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private record Delivery(String yosCode, String body) {
    }

    @BeforeAll
    static void startStubs() throws IOException {
        stubs = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubs.createContext("/internal/subscriptions/" + YOS_A, exchange -> answerSubscription(exchange, YOS_A));
        stubs.createContext("/internal/subscriptions/" + YOS_B, exchange -> answerSubscription(exchange, YOS_B));
        stubs.createContext("/internal/subscriptions/" + UNSUBSCRIBED_YOS, exchange -> respond(exchange, 404, ""));
        stubs.createContext("/yos/" + YOS_A + "/olay-dinleme", exchange -> receiveDelivery(exchange, YOS_A));
        stubs.createContext("/yos/" + YOS_B + "/olay-dinleme", exchange -> receiveDelivery(exchange, YOS_B));
        stubs.start();
    }

    @AfterAll
    static void stopStubs() {
        stubs.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStubs(DynamicPropertyRegistry registry) {
        registry.add("obe.subscription-service-url", EventDeliveryFlowTest::stubUrl);
        registry.add("obe.listener-urls.8001", () -> stubUrl() + "/yos/" + YOS_A);
        registry.add("obe.listener-urls.8002", () -> stubUrl() + "/yos/" + YOS_B);
    }

    @BeforeEach
    void clean() {
        deliveries.clear();
        downYoses.clear();
        subscriptionServiceDown = false;
        subscriptionLookups.set(0);
        repository.deleteAll();
        redisTemplate.delete(List.of(SubscriptionCacheKeys.of(YOS_A), SubscriptionCacheKeys.of(YOS_B),
                SubscriptionCacheKeys.of(UNSUBSCRIBED_YOS)));
    }

    @Test
    void asksForASubscriptionOnlyOnce() {
        NotificationEvent first = event(YOS_A, ResourceType.ODEME_EMRI);
        NotificationEvent second = event(YOS_A, ResourceType.ODEME_EMRI);

        send(first);
        awaitStatus(first, DeliveryStatus.DELIVERED);
        send(second);
        awaitStatus(second, DeliveryStatus.DELIVERED);

        assertThat(subscriptionLookups).hasValue(1);
    }

    @Test
    void remembersThatYosHasNoSubscription() {
        NotificationEvent before = event(UNSUBSCRIBED_YOS, ResourceType.ODEME_EMRI);
        NotificationEvent marker = event(YOS_A, ResourceType.ODEME_EMRI);
        send(before);
        send(marker);
        awaitStatus(marker, DeliveryStatus.DELIVERED);
        assertThat(redisTemplate.opsForValue().get(SubscriptionCacheKeys.of(UNSUBSCRIBED_YOS))).isEqualTo("none");
    }

    @Test
    void deliversSubscribedEventToYos() {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);

        send(event);

        awaitStatus(event, DeliveryStatus.DELIVERED);
        String body = deliveriesOf(event).get(0).body();
        assertThat(JsonPath.<String>read(body, "$.katilimciBlg.hhsKod")).isEqualTo("9999");
        assertThat(JsonPath.<String>read(body, "$.katilimciBlg.yosKod")).isEqualTo(YOS_A);
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].olayNo")).isEqualTo(event.eventNo());
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].olayTipi")).isEqualTo("KAYNAK_GUNCELLENDI");
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].kaynakTipi")).isEqualTo("ODEME_EMRI");
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].kaynakNo")).isEqualTo(event.resourceNo());
    }

    @Test
    void doesNotDeliverWhatWasNotSubscribed() {
        NotificationEvent otherType = event(YOS_A, ResourceType.KART_BILGILERI);
        NotificationEvent noSubscription = event(UNSUBSCRIBED_YOS, ResourceType.ODEME_EMRI);
        NotificationEvent marker = event(YOS_A, ResourceType.ODEME_EMRI);

        // The marker arrives after the others, so waiting for it proves they were processed
        send(otherType);
        send(noSubscription);
        send(marker);

        awaitStatus(marker, DeliveryStatus.DELIVERED);
        assertThat(repository.findById(otherType.eventNo())).isEmpty();
        assertThat(repository.findById(noSubscription.eventNo())).isEmpty();
    }

    @Test
    void keepsEventWhileSubscriptionServiceIsDown() throws Exception {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);
        subscriptionServiceDown = true;

        send(event);

        Thread.sleep(OUTAGE.toMillis());
        assertThat(repository.findById(event.eventNo())).isEmpty();
        subscriptionServiceDown = false;
        awaitStatus(event, DeliveryStatus.DELIVERED);
    }

    @Test
    void yosThatIsDownDoesNotHoldBackAnother() {
        NotificationEvent toDownYos = event(YOS_A, ResourceType.ODEME_EMRI);
        NotificationEvent toHealthyYos = event(YOS_B, ResourceType.ODEME_EMRI);
        downYoses.add(YOS_A);

        // Same key, so both share a partition and would block each other in a consumer retry
        send(toDownYos);
        send(toHealthyYos);

        awaitStatus(toHealthyYos, DeliveryStatus.DELIVERED);
        EventDelivery waiting = delivery(toDownYos);
        assertThat(waiting.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(waiting.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void retriesAtTheScheduledTimeAndSucceeds() {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);
        downYoses.add(YOS_A);

        send(event);
        awaitFailedAttempts(event, 1);
        assertThat(delivery(event).getNextAttemptAt()).isAfter(Instant.now());

        downYoses.remove(YOS_A);
        makeDueNow(event);

        awaitStatus(event, DeliveryStatus.DELIVERED);
    }

    @Test
    void doesNotRetryBeforeTheScheduledTime() throws Exception {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);
        downYoses.add(YOS_A);

        send(event);
        awaitFailedAttempts(event, 1);
        Thread.sleep(OUTAGE.toMillis());

        assertThat(deliveriesOf(event)).hasSize(1);
    }

    @Test
    void becomesUndeliveredAfterTheLastRetry() {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);
        downYoses.add(YOS_A);

        send(event);
        for (int failures = 1; failures <= 3; failures++) {
            awaitFailedAttempts(event, failures);
            makeDueNow(event);
        }

        awaitStatus(event, DeliveryStatus.UNDELIVERED);
        assertThat(deliveriesOf(event)).hasSize(4);
    }

    @Test
    void balanceEventIsNotRetried() {
        NotificationEvent event = event(YOS_A, ResourceType.BAKIYE);
        downYoses.add(YOS_A);

        send(event);

        awaitStatus(event, DeliveryStatus.UNDELIVERED);
        assertThat(deliveriesOf(event)).hasSize(1);
    }

    @Test
    void redeliveredMessageIsSentOnce() {
        NotificationEvent event = event(YOS_A, ResourceType.ODEME_EMRI);
        NotificationEvent marker = event(YOS_A, ResourceType.ODEME_EMRI);

        send(event);
        awaitStatus(event, DeliveryStatus.DELIVERED);
        send(event);
        send(marker);

        awaitStatus(marker, DeliveryStatus.DELIVERED);
        assertThat(deliveriesOf(event)).hasSize(1);
    }

    @Test
    void keepsOneUndeliveredRecordPerResource() {
        String resourceNo = UUID.randomUUID().toString();
        NotificationEvent older = event(YOS_A, ResourceType.BAKIYE, resourceNo);
        NotificationEvent newer = event(YOS_A, ResourceType.BAKIYE, resourceNo);
        downYoses.add(YOS_A);

        send(older);
        awaitStatus(older, DeliveryStatus.UNDELIVERED);
        send(newer);
        awaitStatus(newer, DeliveryStatus.UNDELIVERED);

        assertThat(delivery(older).getStatus()).isEqualTo(DeliveryStatus.SUPERSEDED);
    }

    private void send(NotificationEvent event) {
        // A fixed key keeps these on one partition, so the order of the test holds
        producer.send(NotificationTopics.EVENTS, "test", event).join();
    }

    private void makeDueNow(NotificationEvent event) {
        jdbc.update("UPDATE event_delivery SET next_attempt_at = SYSTIMESTAMP - 1 WHERE event_no = ?",
                event.eventNo());
    }

    private void awaitStatus(NotificationEvent event, DeliveryStatus status) {
        await().atMost(WAIT_LIMIT).until(() -> repository.findById(event.eventNo())
                .filter(delivery -> delivery.getStatus() == status)
                .isPresent());
    }

    private void awaitFailedAttempts(NotificationEvent event, int failures) {
        await().atMost(WAIT_LIMIT).until(() -> repository.findById(event.eventNo())
                .filter(delivery -> delivery.getFailedAttempts() >= failures)
                .isPresent());
    }

    private EventDelivery delivery(NotificationEvent event) {
        return repository.findById(event.eventNo()).orElseThrow();
    }

    private List<Delivery> deliveriesOf(NotificationEvent event) {
        return deliveries.stream().filter(delivery -> delivery.body().contains(event.eventNo())).toList();
    }

    private NotificationEvent event(String yosCode, ResourceType resourceType) {
        return event(yosCode, resourceType, UUID.randomUUID().toString());
    }

    private NotificationEvent event(String yosCode, ResourceType resourceType, String resourceNo) {
        return new NotificationEvent(UUID.randomUUID().toString(), yosCode, EventType.KAYNAK_GUNCELLENDI, resourceType,
                resourceNo, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static String stubUrl() {
        return "http://localhost:" + stubs.getAddress().getPort();
    }

    private static void answerSubscription(HttpExchange exchange, String yosCode) throws IOException {
        subscriptionLookups.incrementAndGet();
        if (subscriptionServiceDown) {
            respond(exchange, 503, "");
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, 200, "{\"yosCode\":\"%s\",\"types\":%s}".formatted(yosCode, TYPES));
    }

    private static void receiveDelivery(HttpExchange exchange, String yosCode) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        deliveries.add(new Delivery(yosCode, body));
        respond(exchange, downYoses.contains(yosCode) ? 503 : 202, "");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
