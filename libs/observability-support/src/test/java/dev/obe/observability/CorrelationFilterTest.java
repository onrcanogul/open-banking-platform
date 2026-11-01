package dev.obe.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationFilterTest {

    private final CorrelationFilter filter = new CorrelationFilter();
    private final AtomicReference<String> seenInRequest = new AtomicReference<>();
    private final FilterChain chain = (request, response) -> seenInRequest.set(Correlation.current());

    @Test
    void keepsAValidCallerId() throws Exception {
        MockHttpServletResponse response = run("abc-123");

        assertThat(seenInRequest).hasValue("abc-123");
        assertThat(response.getHeader(Correlation.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void createsAnIdWhenCallerSendsNone() throws Exception {
        MockHttpServletResponse response = run(null);

        assertThat(seenInRequest.get()).isNotBlank();
        assertThat(response.getHeader(Correlation.HEADER)).isEqualTo(seenInRequest.get());
    }

    @Test
    void replacesAnIdThatCouldForgeLogLines() throws Exception {
        run("abc\n2026-11-01 ERROR forged line");

        assertThat(seenInRequest.get()).doesNotContain("forged");
    }

    @Test
    void replacesAnIdThatIsTooLong() throws Exception {
        String tooLong = "a".repeat(37);

        run(tooLong);

        assertThat(seenInRequest.get()).isNotEqualTo(tooLong);
    }

    @Test
    void leavesNoIdBehindOnTheThread() throws Exception {
        run("abc-123");

        assertThat(Correlation.current()).isNull();
    }

    @Test
    void runRestoresThePreviousId() {
        Correlation.run("outer", () -> {
            Correlation.run("inner", () -> assertThat(Correlation.current()).isEqualTo("inner"));
            assertThat(Correlation.current()).isEqualTo("outer");
        });

        assertThat(Correlation.current()).isNull();
    }

    private MockHttpServletResponse run(String incoming) throws Exception {
        HttpServletRequest request = requestWith(incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private HttpServletRequest requestWith(String incoming) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/olay-abonelik");
        if (incoming != null) {
            request.addHeader(Correlation.HEADER, incoming);
        }
        return request;
    }
}
