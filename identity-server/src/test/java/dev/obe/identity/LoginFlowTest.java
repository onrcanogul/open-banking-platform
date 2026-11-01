package dev.obe.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Walks the same steps a browser takes, from the first redirect to the token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"REDIS_PASSWORD=unused", "IDENTITY_VIEWER_PASSWORD=viewer-test",
                "IDENTITY_RECONCILER_PASSWORD=reconciler-test"})
@Testcontainers
class LoginFlowTest {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final String VERIFIER = "a-long-random-value-that-only-the-browser-application-knows";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8.10.3-alpine").withExposedPorts(6379);

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void publishesItsConfiguration() throws Exception {
        HttpResponse<String> response = get("/.well-known/openid-configuration");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("authorization_endpoint", "token_endpoint", "jwks_uri");
    }

    @Test
    void sendsAnUnknownVisitorToTheLoginPage() throws Exception {
        HttpResponse<String> response = get(authorizePath());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).contains("/login");
    }

    @Test
    void refusesAnAuthorizationRequestWithoutPkce() throws Exception {
        String path = "/oauth2/authorize?response_type=code&client_id=operations-ui&scope=openid&redirect_uri="
                + encode(REDIRECT_URI);

        HttpResponse<String> response = get(path);

        assertThat(location(response)).contains("error=");
    }

    @Test
    void wrongPasswordGivesNoCode() throws Exception {
        HttpResponse<String> response = logIn("viewer", "wrong-password");

        assertThat(location(response)).contains("/login?error");
    }

    @Test
    void tellsTheUserThePasswordWasWrong() throws Exception {
        assertThat(get("/login").body()).contains("Personel Girişi").doesNotContain("hatalı");
        assertThat(get("/login?error").body()).contains("Kullanıcı adı veya şifre hatalı.");
    }

    @Test
    void healthStaysOpenForProbes() throws Exception {
        HttpRequest probe = HttpRequest.newBuilder(uri("/actuator/health")).GET().build();

        assertThat(client.send(probe, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    @Test
    void viewerGetsATokenWithItsRole() throws Exception {
        String token = accessToken("viewer", "viewer-test");

        assertThat(rolesOf(token)).containsExactly("VIEWER");
    }

    @Test
    void reconcilerGetsBothRoles() throws Exception {
        String token = accessToken("reconciler", "reconciler-test");

        assertThat(rolesOf(token)).containsExactlyInAnyOrder("VIEWER", "RECONCILER");
    }

    @Test
    void aStolenCodeIsUselessWithoutTheVerifier() throws Exception {
        String code = code("viewer", "viewer-test");

        HttpResponse<String> response = exchange(code, "a-different-verifier-chosen-by-whoever-stole-the-code");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void aCodeWorksOnlyOnce() throws Exception {
        String code = code("viewer", "viewer-test");
        assertThat(exchange(code, VERIFIER).statusCode()).isEqualTo(200);

        assertThat(exchange(code, VERIFIER).statusCode()).isEqualTo(400);
    }

    @Test
    void anOpenSessionGetsANewCodeWithoutAskingAgain() throws Exception {
        code("viewer", "viewer-test");

        HttpResponse<String> response = get(authorizePath());

        assertThat(location(response)).startsWith(REDIRECT_URI).contains("code=");
    }

    @Test
    void allowsTheUiOriginToCallTheTokenEndpoint() throws Exception {
        HttpResponse<String> response = preflight("http://localhost:5173");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
    }

    @Test
    void refusesAnotherOrigin() throws Exception {
        HttpResponse<String> response = preflight("http://evil.example");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private String accessToken(String username, String password) throws Exception {
        HttpResponse<String> response = exchange(code(username, password), VERIFIER);
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private String code(String username, String password) throws Exception {
        logIn(username, password);
        String redirect = location(get(authorizePath()));
        assertThat(redirect).startsWith(REDIRECT_URI);
        return redirect.substring(redirect.indexOf("code=") + 5).split("&")[0];
    }

    private HttpResponse<String> logIn(String username, String password) throws Exception {
        get(authorizePath());
        Matcher csrf = CSRF.matcher(get("/login").body());
        assertThat(csrf.find()).isTrue();
        String form = "username=" + encode(username) + "&password=" + encode(password) + "&_csrf=" + encode(csrf.group(1));
        return post("/login", form);
    }

    private HttpResponse<String> exchange(String code, String verifier) throws Exception {
        return post("/oauth2/token", "grant_type=authorization_code&client_id=operations-ui&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI) + "&code_verifier=" + encode(verifier));
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/oauth2/token"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String authorizePath() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return "/oauth2/authorize?response_type=code&client_id=operations-ui&scope=openid&redirect_uri="
                + encode(REDIRECT_URI) + "&code_challenge_method=S256&code_challenge=" + challenge;
    }

    private List<String> rolesOf(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.roles");
    }

    private HttpResponse<String> get(String path) throws Exception {
        // Security redirects only a client that accepts a page, as a browser does
        HttpRequest request = HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
