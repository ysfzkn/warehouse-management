package com.warehouse.assistant.core.ratelimit;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssistantRateLimiterTest {

    private AssistantRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        props.getRatelimit().setGuestSessionMax(2);
        props.getRatelimit().setGuestIpDaily(5);
        props.getRatelimit().setCustomerHourly(3);
        props.getRatelimit().setCustomerDaily(10);
        props.getRatelimit().setWmsHourly(4);
        limiter = new AssistantRateLimiter(props);
    }

    @Test
    void guestSessionAllowedThenDenied() {
        String sid = "guest-test-1";
        RateLimitDecision d1 = limiter.checkGuestSession(sid);
        assertTrue(d1.allowed(), "First message should be allowed");

        RateLimitDecision d2 = limiter.checkGuestSession(sid);
        assertTrue(d2.allowed(), "Second message should be allowed");

        RateLimitDecision d3 = limiter.checkGuestSession(sid);
        assertFalse(d3.allowed(), "Third message should be denied (limit=2)");
        assertEquals(RateLimitScope.GUEST_SESSION, d3.scope());
        assertTrue(d3.reachedGuestLimit(), "Should indicate guest limit reached");
    }

    @Test
    void differentGuestSessionsAreIsolated() {
        String sid1 = "guest-a";
        String sid2 = "guest-b";
        limiter.checkGuestSession(sid1);
        limiter.checkGuestSession(sid1);
        // sid1 exhausted, sid2 should still work
        RateLimitDecision d = limiter.checkGuestSession(sid2);
        assertTrue(d.allowed());
    }

    @Test
    void customerHourlyLimitEnforced() {
        long cid = 42L;
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.checkCustomerHourly(cid).allowed(), "Message " + (i + 1) + " should pass");
        }
        RateLimitDecision deny = limiter.checkCustomerHourly(cid);
        assertFalse(deny.allowed(), "4th message should be denied (hourly limit=3)");
        assertEquals(RateLimitScope.CUSTOMER_HOURLY, deny.scope());
        assertFalse(deny.reachedGuestLimit());
    }

    @Test
    void wmsHourlyLimitEnforced() {
        String user = "admin";
        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.checkWmsHourly(user).allowed());
        }
        assertFalse(limiter.checkWmsHourly(user).allowed());
    }

    @Test
    void retryAfterIsPositive() {
        String sid = "retry-test";
        limiter.checkGuestSession(sid);
        limiter.checkGuestSession(sid);
        RateLimitDecision deny = limiter.checkGuestSession(sid);
        assertTrue(deny.retryAfterSeconds() > 0, "retryAfterSeconds should be positive");
    }
}
