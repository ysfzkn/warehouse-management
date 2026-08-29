package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP request throttling.
 *
 * <p>The previous version guarded six exact paths and only for {@code POST}, which left
 * every other abusable endpoint wide open: guest checkout could be replayed to reserve
 * stock indefinitely, order tracking could be brute-forced, the cart endpoint could be
 * used to create unbounded rows with random session ids, and the AI assistant — the one
 * endpoint that costs real money per call — had no HTTP-level ceiling at all.</p>
 *
 * <p>Rules are now ant-pattern based (so {@code /products/{id}/notify-me} can be
 * matched), method aware, and backed by a catch-all bucket that bounds total traffic
 * from a single address. First matching rule wins, so specific rules are listed before
 * the catch-all.</p>
 *
 * <p>State is per-instance and in memory. On a single Railway container that is exact;
 * with several replicas each one enforces its own share, which weakens but does not
 * remove the limit. Moving the counters to Redis is the fix when the app is scaled out.</p>
 */
@Service
public class RateLimitService {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Never throttled: provider callbacks retry aggressively by design. */
    private static final List<String> EXEMPT_PATTERNS = List.of(
            "/api/store/payment/callback/**",
            "/api/store/payment/callback",
            "/api/admin/cargo/webhook/**",
            "/api/admin/invoice/webhook/**",
            "/api/public/cargo/**",
            "/actuator/health",
            "/actuator/health/**"
    );

    private record Rule(String id, String pattern, Set<String> methods, int maxRequests, Duration window) {
        boolean matches(String uri, String method) {
            return (methods.isEmpty() || methods.contains(method))
                    && MATCHER.match(pattern, uri);
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private final java.util.Map<String, Cache<String, AtomicInteger>> caches = new java.util.concurrent.ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.security.ratelimit.global-per-minute:600}") int globalPerMinute) {

        // ── Credential endpoints: brute-force and enumeration ──────────────────
        register("admin-login", "/api/admin/auth/login", Set.of("POST"), 5, Duration.ofMinutes(15));
        register("store-login", "/api/store/auth/login", Set.of("POST"), 5, Duration.ofMinutes(15));
        register("store-register", "/api/store/auth/register", Set.of("POST"), 3, Duration.ofHours(1));
        register("forgot-password", "/api/store/auth/forgot-password", Set.of("POST"), 3, Duration.ofHours(1));
        // Reset/verify/complete consume a token from an email. Unlimited attempts would
        // let an attacker grind the token space, however large it is.
        register("reset-password", "/api/store/auth/reset-password", Set.of("POST"), 10, Duration.ofHours(1));
        register("verify-email", "/api/store/auth/verify-email", Set.of("POST"), 20, Duration.ofHours(1));
        register("complete-account", "/api/store/auth/complete-account", Set.of("POST"), 10, Duration.ofHours(1));
        register("google-auth", "/api/store/auth/google", Set.of("POST"), 10, Duration.ofMinutes(15));
        register("token-refresh", "/api/store/auth/refresh", Set.of("POST"), 30, Duration.ofMinutes(15));

        // ── Order lifecycle: stock reservation abuse and order enumeration ─────
        register("guest-checkout", "/api/store/checkout/guest-checkout", Set.of("POST"), 5, Duration.ofMinutes(10));
        register("place-order", "/api/store/checkout/place-order", Set.of("POST"), 10, Duration.ofMinutes(10));
        register("payment-init", "/api/store/payment/initialize", Set.of("POST"), 10, Duration.ofMinutes(10));
        register("order-track", "/api/store/public/orders/track", Set.of("POST"), 10, Duration.ofMinutes(10));
        register("order-confirm", "/api/store/public/orders/confirm/**", Set.of(), 20, Duration.ofMinutes(10));

        // ── Unauthenticated write endpoints: spam and unbounded row creation ───
        register("contact", "/api/store/contact-messages", Set.of("POST"), 3, Duration.ofMinutes(1));
        register("newsletter", "/api/store/newsletter/**", Set.of("POST"), 5, Duration.ofHours(1));
        register("notify-me", "/api/store/products/*/notify-me", Set.of("POST"), 10, Duration.ofHours(1));
        register("review-write", "/api/store/products/*/reviews", Set.of("POST"), 10, Duration.ofHours(1));
        register("cart", "/api/store/cart/**", Set.of("POST", "PUT", "DELETE"), 120, Duration.ofMinutes(1));

        // ── AI assistant: every call has a direct token cost ──────────────────
        // The in-app guard limits per guest-session cookie and per hashed IP, both of
        // which a client can churn. This is the hard ceiling that cannot be reset by
        // dropping a cookie.
        register("assistant-store", "/api/store/assistant/**", Set.of("POST"), 40, Duration.ofHours(1));
        register("assistant-wms", "/api/cezeri/**", Set.of("POST"), 90, Duration.ofHours(1));

        // ── Catch-all ─────────────────────────────────────────────────────────
        if (globalPerMinute > 0) {
            register("global", "/api/**", Set.of(), globalPerMinute, Duration.ofMinutes(1));
        }
    }

    private void register(String id, String pattern, Set<String> methods, int maxRequests, Duration window) {
        rules.add(new Rule(id, pattern, methods, maxRequests, window));
        caches.put(id, Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(50_000)
                .build());
    }

    /** Returns the id of the first matching rule, or null when the request is unlimited. */
    public String findMatchingRule(String requestUri, String method) {
        if (requestUri == null) return null;
        for (String exempt : EXEMPT_PATTERNS) {
            if (MATCHER.match(exempt, requestUri)) return null;
        }
        String upperMethod = method == null ? "" : method.toUpperCase();
        for (Rule rule : rules) {
            if (rule.matches(requestUri, upperMethod)) {
                return rule.id();
            }
        }
        return null;
    }

    public boolean isRateLimited(String ruleId, String clientIp) {
        Rule rule = ruleById(ruleId);
        Cache<String, AtomicInteger> cache = caches.get(ruleId);
        if (rule == null || cache == null) return false;
        AtomicInteger counter = cache.get(clientIp, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > rule.maxRequests();
    }

    public long getRetryAfterSeconds(String ruleId) {
        Rule rule = ruleById(ruleId);
        return rule != null ? rule.window().getSeconds() : 60;
    }

    private Rule ruleById(String id) {
        for (Rule rule : rules) {
            if (rule.id().equals(id)) return rule;
        }
        return null;
    }

    /**
     * Backwards-compatible entry point kept for existing tests: resolves a POST rule for
     * the given URI.
     */
    public String findMatchingPath(String requestUri) {
        return findMatchingRule(requestUri, "POST");
    }
}
