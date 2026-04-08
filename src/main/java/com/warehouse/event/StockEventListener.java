package com.warehouse.event;

import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StockEventListener {

    private final CacheManager cacheManager;

    public StockEventListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Async
    @EventListener
    public void handleStockChanged(StockChangedEvent event) {
        // Evict storefront caches for affected product
        evictCache("stockAvailability", event.getProductId());
        evictCache("storeCatalog", event.getProductId());
        evictCache("productDetail", event.getProductId());

        // Also evict general dashboard caches
        evictCache("lowStockItems", null);
        evictCache("outOfStockItems", null);
        evictCache("counts", "lowStock");
    }

    private void evictCache(String cacheName, Object key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            if (key != null) {
                cache.evict(key);
            } else {
                cache.clear();
            }
        }
    }
}
