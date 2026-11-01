package dev.obe.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Works out who the partner is from its API key. The partner header sent onward always comes from
 * here, so a caller cannot act as another partner by writing that header itself.
 */
@Component
class PartnerAuthenticationFilter extends OncePerRequestFilter implements Ordered {

    private static final String API_KEY = "X-Api-Key";
    private static final String PARTNER_CODE = "X-Partner-Code";

    private final GatewayProperties properties;

    PartnerAuthenticationFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> partner = partnerOf(request.getHeader(API_KEY));
        if (partner.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        chain.doFilter(new PartnerRequest(request, partner.get()), response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/disbursements");
    }

    /** Runs after the correlation filter, so rejected calls can be traced too. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private Optional<String> partnerOf(String apiKey) {
        if (apiKey == null) {
            return Optional.empty();
        }
        return properties.partnerApiKeys().entrySet().stream()
                .filter(entry -> sameKey(entry.getValue(), apiKey))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // Takes the same time wherever the keys differ, so timing reveals nothing about a key
    private boolean sameKey(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** Replaces the caller's partner header and hides the API key from the services behind. */
    private static final class PartnerRequest extends HttpServletRequestWrapper {

        private final String partnerCode;

        private PartnerRequest(HttpServletRequest request, String partnerCode) {
            super(request);
            this.partnerCode = partnerCode;
        }

        @Override
        public String getHeader(String name) {
            if (PARTNER_CODE.equalsIgnoreCase(name)) {
                return partnerCode;
            }
            return API_KEY.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (PARTNER_CODE.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(partnerCode));
            }
            return API_KEY.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.removeIf(name -> API_KEY.equalsIgnoreCase(name) || PARTNER_CODE.equalsIgnoreCase(name));
            names.add(PARTNER_CODE);
            return Collections.enumeration(names);
        }
    }
}
