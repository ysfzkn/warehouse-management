package com.warehouse.service;

import com.warehouse.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // 0 disables the catch-all bucket so each test exercises the rule it names.
        rateLimitService = new RateLimitService(0);
    }

    @Test
    void should_allow_requests_under_limit() {
        String path = rateLimitService.findMatchingRule("/api/store/auth/login", "POST");
        String ip = "192.168.1.1";
        assertThat(path).isNotNull();

        // Login limit is 5 per 15 min
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitService.isRateLimited(path, ip)).isFalse();
        }
    }

    @Test
    void should_block_requests_over_limit() {
        String path = rateLimitService.findMatchingRule("/api/store/auth/login", "POST");
        String ip = "10.0.0.1";
        assertThat(path).isNotNull();

        // Exhaust the 5-request limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.isRateLimited(path, ip);
        }

        // 6th request should be blocked
        assertThat(rateLimitService.isRateLimited(path, ip)).isTrue();
    }

    /**
     * A spoofed X-Forwarded-For used to buy a fresh bucket per request. The service is
     * keyed purely on the address it is given, so this pins the contract the
     * ClientIpResolver upholds: the same client keeps the same bucket.
     */
    @Test
    void counts_are_per_client_key_not_per_request() {
        String rule = rateLimitService.findMatchingRule("/api/store/auth/login", "POST");
        for (int i = 0; i < 5; i++) {
            rateLimitService.isRateLimited(rule, "198.51.100.7");
        }
        assertThat(rateLimitService.isRateLimited(rule, "198.51.100.7")).isTrue();
        assertThat(rateLimitService.isRateLimited(rule, "198.51.100.8"))
                .as("a genuinely different address still gets its own bucket")
                .isFalse();
    }

    @Test
    void unlisted_paths_are_not_throttled_when_the_catch_all_is_disabled() {
        assertThat(rateLimitService.findMatchingRule("/api/store/products/1", "GET")).isNull();
    }

    @Test
    void provider_callbacks_are_never_throttled() {
        RateLimitService withGlobal = new RateLimitService(600);
        assertThat(withGlobal.findMatchingRule("/api/store/payment/callback", "POST")).isNull();
        assertThat(withGlobal.findMatchingRule("/api/public/cargo/kargonomi/webhook", "POST")).isNull();
    }

    @Test
    void the_catch_all_covers_paths_without_a_specific_rule() {
        RateLimitService withGlobal = new RateLimitService(600);
        assertThat(withGlobal.findMatchingRule("/api/store/products/1", "GET")).isEqualTo("global");
    }

    @Test
    void order_tracking_has_its_own_bucket() {
        RateLimitService svc = new RateLimitService(0);
        String rule = svc.findMatchingRule("/api/store/public/orders/track", "POST");
        assertThat(rule).isEqualTo("order-track");
        for (int i = 0; i < 10; i++) {
            assertThat(svc.isRateLimited(rule, "127.0.0.1")).isFalse();
        }
        assertThat(svc.isRateLimited(rule, "127.0.0.1")).isTrue();
    }

    @Test
    void should_return_correct_retry_after() {
        long retryAfter = rateLimitService.getRetryAfterSeconds(
                rateLimitService.findMatchingRule("/api/store/auth/login", "POST"));
        assertThat(retryAfter).isEqualTo(900); // 15 minutes = 900 seconds

        long registerRetry = rateLimitService.getRetryAfterSeconds(
                rateLimitService.findMatchingRule("/api/store/auth/register", "POST"));
        assertThat(registerRetry).isEqualTo(3600); // 1 hour
    }
}
