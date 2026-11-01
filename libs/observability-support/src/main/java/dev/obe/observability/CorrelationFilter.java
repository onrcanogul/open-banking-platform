package dev.obe.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives each request a correlation id. A caller's value goes into every log line, so anything
 * outside the standard's format is replaced rather than trusted, which also stops log injection.
 */
public class CorrelationFilter extends OncePerRequestFilter implements Ordered {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,36}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(Correlation.HEADER);
        boolean usable = incoming != null && VALID_ID.matcher(incoming).matches();
        String correlationId = usable ? incoming : Correlation.newId();
        MDC.put(Correlation.MDC_KEY, correlationId);
        response.setHeader(Correlation.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Server threads are reused, so a leftover id would mark the next request
            MDC.remove(Correlation.MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
