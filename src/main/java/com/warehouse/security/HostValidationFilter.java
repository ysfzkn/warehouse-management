package com.warehouse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Host header validation: production'da admin endpoint'lerinin sadece
 * yapılandırılmış admin subdomain'inden (wms.* / admin.*) çağrılmasını sağlar.
 *
 * <p>Saldırı senaryosu: saldırgan store domain'inin XSS açığını kullanıp
 * <code>fetch('/api/admin/users')</code> derse → bu filter Host header'ını
 * kontrol eder; store subdomain'inden gelen admin isteği 403 ile reddedilir.</p>
 *
 * <p>Yapılandırma {@code app.hosts.admin} ve {@code app.hosts.store} property'leri
 * ile veya {@code APP_HOSTS_ADMIN} env değişkeniyle. Dev profile'inde
 * (host'lar boşsa) tamamen pas geçer.</p>
 */
@Component
public class HostValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HostValidationFilter.class);

    private final List<String> allowedAdminHosts;
    private final List<String> allowedStoreHosts;
    private final boolean enabled;

    public HostValidationFilter(
            @Value("${app.hosts.admin:}") String adminHostsCsv,
            @Value("${app.hosts.store:}") String storeHostsCsv,
            Environment env) {
        this.allowedAdminHosts = parse(adminHostsCsv);
        this.allowedStoreHosts = parse(storeHostsCsv);
        // Production'da hostlar tanımlıysa enable. Aksi halde no-op (dev/test rahat çalışır).
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        this.enabled = isProd && !allowedAdminHosts.isEmpty();
        log.info("HostValidationFilter: enabled={}, adminHosts={}, storeHosts={}",
                enabled, allowedAdminHosts, allowedStoreHosts);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String host = request.getServerName();
        String uri = request.getRequestURI();

        boolean isAdminEndpoint = uri.startsWith("/api/admin/") || uri.startsWith("/api/cezeri/");
        boolean isStoreEndpoint = uri.startsWith("/api/store/");

        if (isAdminEndpoint && !allowedAdminHosts.isEmpty() && !matchesAny(host, allowedAdminHosts)) {
            log.warn("[HostValidation] Admin endpoint cağrısı reddedildi: host={}, uri={}", host, uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN_HOST\"}");
            return;
        }
        if (isStoreEndpoint && !allowedStoreHosts.isEmpty() && !matchesAny(host, allowedStoreHosts)) {
            log.warn("[HostValidation] Store endpoint cağrısı reddedildi: host={}, uri={}", host, uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN_HOST\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesAny(String host, List<String> patterns) {
        if (host == null) return false;
        String lower = host.toLowerCase();
        return patterns.stream().anyMatch(p -> matchesPattern(lower, p));
    }

    /** Wildcard "*.example.com" + exact match destekler. */
    private boolean matchesPattern(String host, String pattern) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1); // ".example.com"
            return host.endsWith(suffix);
        }
        return host.equalsIgnoreCase(pattern);
    }

    private static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }
}
