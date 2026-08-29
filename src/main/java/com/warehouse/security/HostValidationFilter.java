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
 * Host header validation: in production, ensures admin endpoints are only
 * called from the configured admin subdomain (wms.* / admin.*).
 *
 * <p>Attack scenario: if an attacker uses an XSS hole on the store domain and
 * calls <code>fetch('/api/admin/users')</code> → this filter checks the Host
 * header; an admin request coming from a store subdomain is rejected with 403.</p>
 *
 * <p>Configured via the {@code app.hosts.admin} and {@code app.hosts.store}
 * properties or the {@code APP_HOSTS_ADMIN} env variable. In the dev profile
 * (when hosts are empty) it is bypassed entirely.</p>
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
        // Enable in production if hosts are defined. Otherwise no-op (dev/test runs freely).
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        this.enabled = isProd && !allowedAdminHosts.isEmpty();
        if (isProd && allowedAdminHosts.isEmpty()) {
            // Silently disabling itself is how this protection went missing: APP_HOSTS_ADMIN
            // was never added to the deployment, so the filter logged "enabled=false" once at
            // boot and nobody noticed the store-to-admin barrier was not actually there.
            log.error("HostValidationFilter DEVRE DISI: production'da APP_HOSTS_ADMIN tanımlı değil. "
                    + "Admin endpoint'leri store domaininden de çağrılabilir durumda. "
                    + "Örnek: APP_HOSTS_ADMIN=admin.example.com, APP_HOSTS_STORE=example.com,www.example.com");
        }
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

        // Public read-only image serving (product photos, site banners/assets, logo view).
        // These contain no sensitive data and are shown on the public storefront, so they
        // must load on BOTH the store and admin hosts — exempt from host validation.
        if (isPublicImagePath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAdminEndpoint = uri.startsWith("/api/admin/") || uri.startsWith("/api/cezeri/");
        boolean isStoreEndpoint = uri.startsWith("/api/store/");

        if (isAdminEndpoint && !allowedAdminHosts.isEmpty() && !matchesAny(host, allowedAdminHosts)) {
            log.warn("[HostValidation] Admin endpoint cağrısı reddedildi: host={}, uri={}", host, uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN_HOST\"}");
            return;
        }
        // Store endpoints serve PUBLIC data (settings, logo, catalog). The admin panel
        // legitimately reads them for its own branding (logo, site name), so allow the
        // admin host too — the XSS concern only runs the other way (store → admin).
        if (isStoreEndpoint && !allowedStoreHosts.isEmpty()
                && !matchesAny(host, allowedStoreHosts) && !matchesAny(host, allowedAdminHosts)) {
            log.warn("[HostValidation] Store endpoint cağrısı reddedildi: host={}, uri={}", host, uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN_HOST\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Public read-only image endpoints that must be reachable from any host
     * (storefront banners/assets + product photos + logo view). Read-only GET,
     * no sensitive data — safe to bypass the admin/store host restriction.
     */
    private boolean isPublicImagePath(String uri) {
        if (uri == null) return false;
        return uri.contains("/settings/site/asset/view/")
            || uri.contains("/settings/site/logo/view")
            || (uri.contains("/products/images/") && uri.endsWith("/view"))
            || (uri.contains("/reviews/images/") && uri.endsWith("/view"));
    }

    private boolean matchesAny(String host, List<String> patterns) {
        if (host == null) return false;
        String lower = host.toLowerCase();
        return patterns.stream().anyMatch(p -> matchesPattern(lower, p));
    }

    /** Supports wildcard "*.example.com" + exact match. */
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
