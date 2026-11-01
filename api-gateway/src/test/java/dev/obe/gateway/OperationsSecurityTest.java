package dev.obe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Uses really signed tokens and a published key, so the signature check itself is under test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "GATEWAY_PARTNER1_API_KEY=test-key")
class OperationsSecurityTest {

    private static final String ISSUER = "http://localhost:9000";
    private static final String UI_ORIGIN = "http://localhost:5173";

    private static final List<String> forwarded = new CopyOnWriteArrayList<>();
    private static HttpServer stub;
    private static RSAKey key;

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startStub() throws IOException, JOSEException {
        key = newKey();
        stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stub.createContext("/oauth2/jwks", OperationsSecurityTest::publishKey);
        stub.createContext("/", OperationsSecurityTest::answer);
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + stub.getAddress().getPort();
        registry.add("obe.disbursement-service-url", () -> url);
        registry.add("obe.identity-server-url", () -> url);
    }

    @BeforeEach
    void clean() {
        forwarded.clear();
    }

    @Test
    void viewerCanRead() throws Exception {
        assertThat(get("/operations/findings", token(key, ISSUER, future(), "VIEWER")).statusCode()).isEqualTo(200);
        assertThat(forwarded).containsExactly("/operations/findings");
    }

    @Test
    void refusesAReaderWithoutAToken() throws Exception {
        assertRefused(get("/operations/findings", null), 401);
    }

    @Test
    void refusesATokenSignedByAnotherKey() throws Exception {
        assertRefused(get("/operations/findings", token(newKey(), ISSUER, future(), "VIEWER")), 401);
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        Instant expired = Instant.now().minus(Duration.ofMinutes(5));

        assertRefused(get("/operations/findings", token(key, ISSUER, expired, "VIEWER")), 401);
    }

    @Test
    void refusesATokenFromAnotherIssuer() throws Exception {
        assertRefused(get("/operations/findings", token(key, "http://evil.example", future(), "VIEWER")), 401);
    }

    @Test
    void refusesATokenWithoutARole() throws Exception {
        assertRefused(get("/operations/findings", token(key, ISSUER, future())), 403);
    }

    @Test
    void viewerCannotRunReconciliation() throws Exception {
        assertRefused(post("/operations/reconciliation", token(key, ISSUER, future(), "VIEWER")), 403);
    }

    @Test
    void reconcilerCanRunReconciliation() throws Exception {
        String token = token(key, ISSUER, future(), "VIEWER", "RECONCILER");

        assertThat(post("/operations/reconciliation", token).statusCode()).isEqualTo(200);
        assertThat(forwarded).containsExactly("/operations/reconciliation");
    }

    @Test
    void writesAreClosedUnlessListed() throws Exception {
        String token = token(key, ISSUER, future(), "VIEWER", "RECONCILER");

        assertRefused(post("/operations/findings", token), 403);
    }

    @Test
    void aPartnerKeyDoesNotOpenOperations() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/operations/findings")).header("X-Api-Key", "test-key").GET().build();

        assertRefused(client.send(request, HttpResponse.BodyHandlers.ofString()), 401);
    }

    @Test
    void aStaffTokenDoesNotOpenDisbursements() throws Exception {
        assertRefused(post("/disbursements", token(key, ISSUER, future(), "VIEWER", "RECONCILER")), 401);
    }

    @Test
    void aStaffTokenDoesNotOpenInternalEndpoints() throws Exception {
        assertRefused(post("/internal/reconciliation", token(key, ISSUER, future(), "VIEWER", "RECONCILER")), 403);
    }

    @Test
    void allowsThePanelToCallOperations() throws Exception {
        HttpResponse<String> response = preflight(UI_ORIGIN);

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(UI_ORIGIN);
        assertThat(forwarded).isEmpty();
    }

    @Test
    void refusesAnotherOrigin() throws Exception {
        assertThat(preflight("http://evil.example").headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void healthStaysOpenForProbes() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
    }

    private void assertRefused(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(forwarded).isEmpty();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), token);
    }

    private HttpResponse<String> post(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString("{}")), token);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/operations/findings"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String token(RSAKey signer, String issuer, Instant expiry, String... roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("someone")
                .issuer(issuer)
                .expirationTime(Date.from(expiry))
                .claim("roles", List.of(roles))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        token.sign(new RSASSASigner(signer));
        return token.serialize();
    }

    private static RSAKey newKey() throws JOSEException {
        return new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    private static Instant future() {
        return Instant.now().plus(Duration.ofMinutes(10));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static void publishKey(HttpExchange exchange) throws IOException {
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void answer(HttpExchange exchange) throws IOException {
        forwarded.add(exchange.getRequestURI().getPath());
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
