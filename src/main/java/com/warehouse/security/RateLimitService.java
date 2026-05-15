package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final Map<String, RateLimitConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, Cache<String, AtomicInteger>> caches = new ConcurrentHashMap<>();

    public RateLimitService() {
        registerLimit("/api/store/auth/login", 5, Duration.ofMinutes(15));
        registerLimit("/api/store/auth/register", 3, Duration.ofHours(1));
        registerLimit("/api/store/auth/forgot-password", 3, Duration.ofHours(1));
        registerLimit("/api/store/auth/google", 10, Duration.ofMinutes(15));
        registerLimit("/api/store/contact-messages", 3, Duration.ofMinutes(1));
        // Admin auth: brute-force korumasi — daha agresif limit
        registerLimit("/api/admin/auth/login", 5, Duration.ofMinutes(15));
    }

    private void registerLimit(String path, int maxRequests, Duration window) {
        configs.put(path, new RateLimitConfig(maxRequests, window));
        caches.put(path, Caffeine.newBuilder()
            .expireAfterWrite(window)
            .maximumSize(10000)
            .build());
    }

    public boolean isRateLimited(String path, String clientIp) {
        RateLimitConfig config = configs.get(path);
        if (config == null) return false;

        Cache<String, AtomicInteger> cache = caches.get(path);
        if (cache == null) return false;

        AtomicInteger counter = cache.get(clientIp, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > config.maxRequests;
    }

    public long getRetryAfterSeconds(String path) {
        RateLimitConfig config = configs.get(path);
        return config != null ? config.window.getSeconds() : 60;
    }

    public String findMatchingPath(String requestUri) {
        for (String path : configs.keySet()) {
            if (requestUri.equals(path) || requestUri.startsWith(path + "/")) {
                return path;
            }
        }
        return null;
    }

    private record RateLimitConfig(int maxRequests, Duration window) {}
}
