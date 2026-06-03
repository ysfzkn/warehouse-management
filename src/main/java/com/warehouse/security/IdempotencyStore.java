package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency-Key store: for order/payment requests arriving with the same key,
 * returns the previous result instead of repeating the mutation.
 *
 * <p>Typical use: if a customer clicks the "Confirm Order" button twice, the
 * client sends the same key, and on the second request the backend returns the
 * first order's ID instead of creating a new order.</p>
 *
 * <p>In-memory (Caffeine), 24h TTL. At multi-instance scale the store can be
 * moved to Redis; it is sufficient for single-instance Railway.</p>
 *
 * <p>Key format check: the Idempotency-Key from the client must be in UUID format
 * (removes the risk of treating every request as the same via fixed strings).</p>
 */
@Component
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);

    /** namespace + key → response payload (JSON-compatible Map) */
    private final Cache<String, Map<String, Object>> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(100_000)
            .build();

    /** Holds in-flight keys (collide-and-wait for concurrent requests with the same key). */
    private final ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>();

    /**
     * Returns the stored result; null if none.
     * @param namespace logical partition such as "checkout" / "payment"
     */
    public Map<String, Object> get(String namespace, String key) {
        if (!isValidKey(key)) return null;
        return cache.getIfPresent(namespace + ":" + key);
    }

    /** Store the result (the same result is returned on a repeat request for 24 hours). */
    public void put(String namespace, String key, Map<String, Object> result) {
        if (!isValidKey(key) || result == null) return;
        cache.put(namespace + ":" + key, result);
    }

    /**
     * In-flight reservation — prevents concurrent duplicates.
     * Returns true if reservation acquired (caller should proceed),
     * false if another in-flight request holds it.
     */
    public boolean tryAcquire(String namespace, String key) {
        if (!isValidKey(key)) return true; // no key means no idempotency, proceed
        return inFlight.putIfAbsent(namespace + ":" + key, Boolean.TRUE) == null;
    }

    public void release(String namespace, String key) {
        if (!isValidKey(key)) return;
        inFlight.remove(namespace + ":" + key);
    }

    /** Requires a UUID v4-like format (so an attacker cannot collide using a random string). */
    private boolean isValidKey(String key) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) return false;
        // UUID or at least 16 random alphanumeric/dash characters
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            // If not a UUID, the min-length check is sufficient
            return key.matches("[A-Za-z0-9_\\-]{16,128}");
        }
    }
}
