package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin login için username-bazlı brute-force koruması.
 *
 * <p>RateLimitFilter IP başına global limit uyguluyor (tüm endpoint için);
 * bu tracker ayrıca kullanıcı adına özel sayar — saldırgan bot havuzundan
 * farklı IP'lerle aynı admin'i deniyorsa yine kilitlenir.</p>
 *
 * <p>Sınırlar:
 * <ul>
 *   <li>15 dakika içinde 5 başarısız deneme → 15 dakika kilit</li>
 *   <li>Başarılı login → sayaç sıfırlanır</li>
 * </ul></p>
 *
 * <p>In-memory (Caffeine); multi-instance scale'de her instance kendi sayar.
 * Tek-instance Railway için yeterli; ileride Redis'e taşınabilir.</p>
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

    /** Kilitliyse {@code lockedUntil} epoch ms döner, değilse 0. */
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

    /** Yanlış parola denemesini kaydet; eşik aşıldıysa kilitle. */
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) return;
        String k = username.toLowerCase();
        AtomicInteger c = attempts.get(k, x -> new AtomicInteger(0));
        int n = c.incrementAndGet();
        if (n >= MAX_ATTEMPTS) {
            lockedUntil.put(k, Instant.now().plus(LOCK_DURATION));
        }
    }

    /** Başarılı login — sayaçları temizle. */
    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) return;
        attempts.invalidate(username.toLowerCase());
        lockedUntil.invalidate(username.toLowerCase());
    }
}
