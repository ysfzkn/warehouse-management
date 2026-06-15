package com.warehouse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds browser/CDN cache headers to <strong>public catalog</strong> GET responses
 * (product listings, product detail, categories, CMS pages). These are identical
 * for every visitor and change slowly, so a short shared cache window cuts repeat
 * traffic and speeds up navigation. A modest {@code max-age} keeps stock/price
 * freshness acceptable, and {@code stale-while-revalidate} hides the refresh latency.
 *
 * <p>User-specific endpoints (cart, orders, account, auth, assistant) are never
 * matched, so no personalized data is ever cached. Conditional 304 handling is
 * provided separately by {@link com.warehouse.config.CatalogCacheConfig}'s ETag filter.
 */
@Component
public class CacheControlFilter extends OncePerRequestFilter {

    /** Public, visitor-independent catalog path prefixes that are safe to cache. */
    private static final String[] CACHEABLE_PREFIXES = {
            "/api/store/products",
            "/api/store/categories",
            "/api/store/pages",
    };

    private static final String CATALOG_CACHE_CONTROL =
            "public, max-age=60, stale-while-revalidate=300";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod()) && isCacheableCatalogPath(request.getRequestURI())) {
            // Set before the chain so it's present when the body is written; a
            // controller may still override it for special cases.
            response.setHeader("Cache-Control", CATALOG_CACHE_CONTROL);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCacheableCatalogPath(String uri) {
        if (uri == null) return false;
        for (String prefix : CACHEABLE_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
