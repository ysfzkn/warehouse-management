package com.warehouse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for dashboard and other frequently accessed data.
 * Uses Caffeine cache for in-memory caching with TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager with different TTLs for different cache types.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "dashboardStats",      // 5 minutes TTL
            "warehouseStats",      // 5 minutes TTL
            "lowStockItems",       // 3 minutes TTL
            "outOfStockItems",    // 3 minutes TTL
            "counts"              // lowStock, unread etc. (short TTL)
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .recordStats());
        
        return cacheManager;
    }

    /**
     * Separate cache manager for low/out of stock items with shorter TTL.
     */
    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(50)
            .recordStats();
    }
}
