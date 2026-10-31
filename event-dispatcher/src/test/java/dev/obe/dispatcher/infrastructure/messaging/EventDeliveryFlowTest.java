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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Runs the dispatcher against a real broker, with plain HTTP stubs for the other services. */
@SpringBootTest(properties = {"obe.redelivery-interval=PT0.5S",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"})
@Testcontainers
class EventDeliveryFlowTest {

    private static final Duration OUTAGE = Duration.ofSeconds(3);
    private static final String SUBSCRIBED_YOS = "8001";
    private static final String UNSUBSCRIBED_YOS = "8002";

    private static final String SUBSCRIPTION = """
            {"yosCode":"8001","types":[{"eventType":"KAYNAK_GUNCELLENDI","resourceType":"ODEME_EMRI"}]}""";

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    private static final List<String> deliveries = new CopyOnWriteArrayList<>();
    private static volatile boolean subscriptionServiceDown;
    private static HttpServer stubs;

    @Autowired
    private KafkaTemplate<String, NotificationEvent> producer;

    @BeforeAll
    static void startStubs() throws IOException {
        stubs = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubs.createContext("/internal/subscriptions/" + SUBSCRIBED_YOS, EventDeliveryFlowTest::answerSubscription);
        stubs.createContext("/internal/subscriptions/" + UNSUBSCRIBED_YOS, exchange -> respond(exchange, 404, ""));
        stubs.createContext("/olay-dinleme", EventDeliveryFlowTest::acceptDelivery);
        stubs.start();
    }

    @AfterAll
    static void stopStubs() {
        stubs.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStubs(DynamicPropertyRegistry registry) {
        registry.add("obe.subscription-service-url", EventDeliveryFlowTest::stubUrl);
        registry.add("obe.listener-urls.8001", EventDeliveryFlowTest::stubUrl);
        registry.add("obe.listener-urls.8002", EventDeliveryFlowTest::stubUrl);
    }

    @BeforeEach
    void clean() {
        deliveries.clear();
    }

    @Test
    void deliversSubscribedEventToYos() {
        NotificationEvent event = event(SUBSCRIBED_YOS, ResourceType.ODEME_EMRI);

        send(event);

        await().atMost(Duration.ofSeconds(30)).until(() -> !deliveries.isEmpty());
        String body = deliveries.get(0);
        assertThat(JsonPath.<String>read(body, "$.katilimciBlg.hhsKod")).isEqualTo("9999");
        assertThat(JsonPath.<String>read(body, "$.katilimciBlg.yosKod")).isEqualTo(SUBSCRIBED_YOS);
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].olayNo")).isEqualTo(event.eventNo());
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].olayTipi")).isEqualTo("KAYNAK_GUNCELLENDI");
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].kaynakTipi")).isEqualTo("ODEME_EMRI");
        assertThat(JsonPath.<String>read(body, "$.olaylar[0].kaynakNo")).isEqualTo(event.resourceNo());
    }

    @Test
    void doesNotDeliverWhatWasNotSubscribed() {
        NotificationEvent otherType = event(SUBSCRIBED_YOS, ResourceType.BAKIYE);
        NotificationEvent noSubscription = event(UNSUBSCRIBED_YOS, ResourceType.ODEME_EMRI);
        NotificationEvent marker = event(SUBSCRIBED_YOS, ResourceType.ODEME_EMRI);

        // The marker arrives after the others, so waiting for it proves they were processed
        send(otherType);
        send(noSubscription);
        send(marker);

        await().atMost(Duration.ofSeconds(30)).until(() -> deliveries.stream()
                .anyMatch(body -> body.contains(marker.eventNo())));
        assertThat(String.join("", deliveries))
                .doesNotContain(otherType.eventNo())
                .doesNotContain(noSubscription.eventNo());
    }

    @Test
    void keepsEventWhileSubscriptionServiceIsDown() throws Exception {
        NotificationEvent event = event(SUBSCRIBED_YOS, ResourceType.ODEME_EMRI);
        subscriptionServiceDown = true;

        send(event);

        Thread.sleep(OUTAGE.toMillis());
        assertThat(deliveries).isEmpty();
        subscriptionServiceDown = false;
        await().atMost(Duration.ofSeconds(30)).until(() -> deliveries.stream()
                .anyMatch(body -> body.contains(event.eventNo())));
    }

    private void send(NotificationEvent event) {
        // A fixed key keeps these on one partition, so the order of the test holds
        producer.send(NotificationTopics.EVENTS, "test", event).join();
    }

    private NotificationEvent event(String yosCode, ResourceType resourceType) {
        return new NotificationEvent(UUID.randomUUID().toString(), yosCode, EventType.KAYNAK_GUNCELLENDI, resourceType,
                UUID.randomUUID().toString(), Instant.now());
    }

    private static String stubUrl() {
        return "http://localhost:" + stubs.getAddress().getPort();
    }

    private static void answerSubscription(HttpExchange exchange) throws IOException {
        if (subscriptionServiceDown) {
            respond(exchange, 503, "");
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, 200, SUBSCRIPTION);
    }

    private static void acceptDelivery(HttpExchange exchange) throws IOException {
        deliveries.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 202, "");
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
