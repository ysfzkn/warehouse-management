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
 * Her HTTP isteğine benzersiz bir <strong>requestId</strong> atar ve SLF4J MDC'ye
 * koyar. Logback JSON encoder'ı bu değeri otomatik olarak her log satırına dahil
 * eder — böylece Grafana Loki'de bir isteğin tüm logları
 * <code>{requestId="abc123"}</code> ile filtrelenebilir.
 *
 * <p>Akış:
 * <ol>
 *   <li>Client {@code X-Request-Id} header'ı gönderebilir (örn. nginx upstream'den)</li>
 *   <li>Yoksa UUID kısaltılmış (8 char) otomatik üretilir</li>
 *   <li>Response header'ında da geri yansıtılır (debug için)</li>
 *   <li>Filter sonunda MDC.clear() ile thread-local temizlenir (thread pool leak önlenir)</li>
 * </ol></p>
 *
 * <p>Ek olarak {@code userId} MDC field'ı login sonrası
 * JwtAuthenticationFilter tarafından doldurulabilir.</p>
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
            // Upstream ID yoksa veya geçersizse kendi ID'mizi üret
            requestId = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_REMOTE_IP, resolveClientIp(request));
            response.setHeader(HEADER_NAME, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // KRİTİK: thread pool'da next request bizim MDC'mizi devralmasın
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_REMOTE_IP);
            // userId / traceId varsa onlar da temizlenmeli (başka filter set'lediyse)
            MDC.remove("userId");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Reverse proxy arkasındaysak X-Forwarded-For; değilse remoteAddr
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
