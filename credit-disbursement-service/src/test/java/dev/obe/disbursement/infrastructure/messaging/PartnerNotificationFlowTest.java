package dev.obe.disbursement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.service.DisbursementService;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import dev.obe.observability.Correlation;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

/** Runs the saga, relay and consumer together against a real broker and database. */
@SpringBootTest(properties = {"ORACLE_BAAS_USER=unused", "ORACLE_BAAS_PASSWORD=unused",
        "obe.saga.poll-interval=PT0.2S", "obe.outbox.poll-interval=PT0.2S", "obe.notification.redelivery-interval=PT0.5S"})
@Testcontainers
class PartnerNotificationFlowTest {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final List<String> notifications = new CopyOnWriteArrayList<>();
    private static volatile boolean partnerDown;
    private static HttpServer partner;

    @Autowired
    private DisbursementService service;

    @MockitoBean
    private CoreBankingClient core;

    @BeforeAll
    static void startPartner() throws IOException {
        partner = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        partner.createContext("/events", PartnerNotificationFlowTest::receive);
        partner.start();
    }

    @AfterAll
    static void stopPartner() {
        partner.stop(0);
    }

    @DynamicPropertySource
    static void pointAtPartner(DynamicPropertyRegistry registry) {
        registry.add("obe.partners.PARTNER1.webhook-url",
                () -> "http://localhost:" + partner.getAddress().getPort() + "/events");
    }

    @BeforeEach
    void clean() {
        notifications.clear();
        partnerDown = false;
    }

    @Test
    void tellsThePartnerHowItEnded() {
        String number = accept();

        await().atMost(WAIT_LIMIT).until(() -> notificationOf(number) != null);

        String body = notificationOf(number);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("TAMAMLANDI");
        assertThat(JsonPath.<String>read(body, "$.eventNo")).isNotBlank();
    }

    @Test
    void keepsInternalFieldsInsideTheBank() {
        AtomicReference<String> number = new AtomicReference<>();
        Correlation.run("internal-id-1", () -> number.set(accept()));

        await().atMost(WAIT_LIMIT).until(() -> notificationOf(number.get()) != null);

        assertThat(notificationOf(number.get()))
                .doesNotContain("internal-id-1")
                .doesNotContain("correlationId")
                .doesNotContain("partnerCode");
    }

    @Test
    void repeatsTheNotificationUntilThePartnerAccepts() throws Exception {
        partnerDown = true;
        String number = accept();
        Thread.sleep(Duration.ofSeconds(3).toMillis());
        assertThat(notificationOf(number)).isNull();

        partnerDown = false;

        await().atMost(WAIT_LIMIT).until(() -> notificationOf(number) != null);
    }

    private String accept() {
        DisbursementDetails details = new DisbursementDetails("PARTNER1", "C-1", "TR01", new BigDecimal("100.00"),
                "TRY");
        return service.accept(details, UUID.randomUUID().toString()).disbursementNo();
    }

    private String notificationOf(String number) {
        return notifications.stream().filter(body -> body.contains(number)).findFirst().orElse(null);
    }

    private static void receive(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (partnerDown) {
            exchange.sendResponseHeaders(503, -1);
        } else {
            notifications.add(body);
            exchange.sendResponseHeaders(200, -1);
        }
        exchange.close();
    }
}
