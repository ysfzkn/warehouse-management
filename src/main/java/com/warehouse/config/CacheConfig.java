package com.warehouse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures application-level caching using Caffeine. The "counts" cache is
 * used to store small, frequently accessed aggregates like unread and low-stock counts.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.counts.ttl-seconds:5}") long ttlSeconds,
            @Value("${app.cache.counts.max-size:100}") long maxSize,
            @Value("${app.cache.refdata.ttl-seconds:60}") long refTtlSeconds,
            @Value("${app.cache.refdata.max-size:1000}") long refMaxSize) {
        CaffeineCacheManager manager = new CaffeineCacheManager("counts", "refdata");
        // Spring's single CaffeineCacheManager uses the same builder for all caches; build a generous config
        // suitable for both small, frequent counts and small reference lists
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Math.max(ttlSeconds, refTtlSeconds), TimeUnit.SECONDS)
                .maximumSize(Math.max(maxSize, refMaxSize)));
        return manager;
    }
}


