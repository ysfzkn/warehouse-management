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
            @Value("${app.cache.counts.max-size:100}") long maxSize) {
        CaffeineCacheManager manager = new CaffeineCacheManager("counts");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize));
        return manager;
    }
}


