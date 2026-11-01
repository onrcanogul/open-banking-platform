package dev.obe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "GATEWAY_PARTNER1_API_KEY=test-key")
class GatewayRoutingTest {

    private static final List<Map<String, List<String>>> forwarded = new CopyOnWriteArrayList<>();
    private static HttpServer service;

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/", GatewayRoutingTest::answer);
        service.start();
    }

    @AfterAll
    static void stopService() {
        service.stop(0);
    }

    @DynamicPropertySource
    static void pointAtService(DynamicPropertyRegistry registry) {
        registry.add("obe.disbursement-service-url", () -> "http://localhost:" + service.getAddress().getPort());
    }

    @BeforeEach
    void clean() {
        forwarded.clear();
    }

    @Test
    void forwardsARequestWithAValidKey() throws Exception {
        HttpResponse<String> response = post("/disbursements", "test-key", null);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(header("X-partner-code")).containsExactly("PARTNER1");
    }

    @Test
    void refusesARequestWithoutAKey() throws Exception {
        assertThat(post("/disbursements", null, null).statusCode()).isEqualTo(401);
        assertThat(forwarded).isEmpty();
    }

    @Test
    void refusesAWrongKey() throws Exception {
        assertThat(post("/disbursements", "wrong-key", null).statusCode()).isEqualTo(401);
        assertThat(forwarded).isEmpty();
    }

    @Test
    void ignoresAPartnerHeaderSentByTheCaller() throws Exception {
        post("/disbursements", "test-key", "OTHER_PARTNER");

        assertThat(header("X-partner-code")).containsExactly("PARTNER1");
    }

    @Test
    void doesNotPassTheApiKeyOn() throws Exception {
        post("/disbursements", "test-key", null);

        assertThat(header("X-api-key")).isNull();
    }

    @Test
    void keepsOtherHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/disbursements"))
                .header("X-Api-Key", "test-key")
                .header("Idempotency-Key", "key-1")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(header("Idempotency-key")).containsExactly("key-1");
    }

    @Test
    void doesNotExposeInternalEndpoints() throws Exception {
        assertThat(post("/internal/reconciliation", "test-key", null).statusCode()).isEqualTo(404);
        assertThat(forwarded).isEmpty();
    }

    private List<String> header(String name) {
        return forwarded.get(0).get(name);
    }

    private HttpResponse<String> post(String path, String apiKey, String partnerCode) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (apiKey != null) {
            request.header("X-Api-Key", apiKey);
        }
        if (partnerCode != null) {
            request.header("X-Partner-Code", partnerCode);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static void answer(HttpExchange exchange) throws IOException {
        forwarded.add(Map.copyOf(exchange.getRequestHeaders()));
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }
}
