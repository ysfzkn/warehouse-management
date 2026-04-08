package com.warehouse.service;

import com.warehouse.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void should_allow_requests_under_limit() {
        String path = "/api/store/auth/login";
        String ip = "192.168.1.1";

        // Login limit is 5 per 15 min
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitService.isRateLimited(path, ip)).isFalse();
        }
    }

    @Test
    void should_block_requests_over_limit() {
        String path = "/api/store/auth/login";
        String ip = "10.0.0.1";

        // Exhaust the 5-request limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.isRateLimited(path, ip);
        }

        // 6th request should be blocked
        assertThat(rateLimitService.isRateLimited(path, ip)).isTrue();
    }

    @Test
    void should_return_correct_retry_after() {
        long retryAfter = rateLimitService.getRetryAfterSeconds("/api/store/auth/login");
        assertThat(retryAfter).isEqualTo(900); // 15 minutes = 900 seconds

        long registerRetry = rateLimitService.getRetryAfterSeconds("/api/store/auth/register");
        assertThat(registerRetry).isEqualTo(3600); // 1 hour
    }
}
