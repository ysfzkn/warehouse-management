package com.warehouse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Preflight requests carry no credentials and must never be throttled, or the
        // browser will report a CORS failure instead of a rate limit.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ruleId = rateLimitService.findMatchingRule(request.getRequestURI(), request.getMethod());
        if (ruleId != null) {
            // Resolved through the trusted-proxy chain: a client-supplied
            // X-Forwarded-For can no longer mint itself a fresh bucket per request.
            String clientIp = clientIpResolver.resolve(request);
            if (rateLimitService.isRateLimited(ruleId, clientIp)) {
                long retryAfter = rateLimitService.getRetryAfterSeconds(ruleId);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Çok fazla istek gönderdiniz. Lütfen " +
                    retryAfter + " saniye sonra tekrar deneyin.\",\"retryAfter\":" + retryAfter + "}"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
