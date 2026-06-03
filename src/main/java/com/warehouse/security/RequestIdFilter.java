package com.warehouse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique <strong>requestId</strong> to every HTTP request and places it
 * in the SLF4J MDC. The Logback JSON encoder automatically includes this value in
 * every log line — so in Grafana Loki all logs for a request can be filtered with
 * <code>{requestId="abc123"}</code>.
 *
 * <p>Flow:
 * <ol>
 *   <li>The client may send an {@code X-Request-Id} header (e.g. from an nginx upstream)</li>
 *   <li>If absent, a shortened (8-char) UUID is generated automatically</li>
 *   <li>It is also reflected back in the response header (for debugging)</li>
 *   <li>At the end of the filter, the thread-local is cleared via MDC.clear() (prevents thread-pool leaks)</li>
 * </ol></p>
 *
 * <p>Additionally, the {@code userId} MDC field may be populated after login by
 * JwtAuthenticationFilter.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_REMOTE_IP = "remoteIp";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            // If there is no upstream ID, or it is invalid, generate our own
            requestId = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_REMOTE_IP, resolveClientIp(request));
            response.setHeader(HEADER_NAME, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: don't let the next request in the thread pool inherit our MDC
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_REMOTE_IP);
            // userId / traceId should also be cleared if present (if another filter set them)
            MDC.remove("userId");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // If behind a reverse proxy, use X-Forwarded-For; otherwise remoteAddr
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real;
        }
        return request.getRemoteAddr();
    }
}
