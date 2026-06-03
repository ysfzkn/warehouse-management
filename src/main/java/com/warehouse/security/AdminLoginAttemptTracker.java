package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Username-based brute-force protection for admin login.
 *
 * <p>RateLimitFilter applies a global per-IP limit (for all endpoints); this tracker
 * additionally counts per username — so if an attacker tries the same admin from
 * different IPs in a bot pool, it still gets locked.</p>
 *
 * <p>Limits:
 * <ul>
 *   <li>5 failed attempts within 15 minutes → 15-minute lock</li>
 *   <li>Successful login → counter is reset</li>
 * </ul></p>
 *
 * <p>In-memory (Caffeine); at multi-instance scale each instance counts on its own.
 * Sufficient for a single-instance Railway deployment; can be moved to Redis later.</p>
 */
@Component
public class AdminLoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .expireAfterWrite(LOCK_DURATION)
            .maximumSize(10_000)
            .build();

    private final Cache<String, Instant> lockedUntil = Caffeine.newBuilder()
            .expireAfterWrite(LOCK_DURATION)
            .maximumSize(10_000)
            .build();

    /** Returns the {@code lockedUntil} epoch ms if locked, otherwise 0. */
    public long lockedUntilMillis(String username) {
        if (username == null || username.isBlank()) return 0;
        Instant until = lockedUntil.getIfPresent(username.toLowerCase());
        if (until == null) return 0;
        if (Instant.now().isAfter(until)) {
            lockedUntil.invalidate(username.toLowerCase());
            return 0;
        }
        return until.toEpochMilli();
    }

    /** Records a wrong-password attempt; locks if the threshold is exceeded. */
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) return;
        String k = username.toLowerCase();
        AtomicInteger c = attempts.get(k, x -> new AtomicInteger(0));
        int n = c.incrementAndGet();
        if (n >= MAX_ATTEMPTS) {
            lockedUntil.put(k, Instant.now().plus(LOCK_DURATION));
        }
    }

    /** Successful login — clears the counters. */
    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) return;
        attempts.invalidate(username.toLowerCase());
        lockedUntil.invalidate(username.toLowerCase());
    }
}
