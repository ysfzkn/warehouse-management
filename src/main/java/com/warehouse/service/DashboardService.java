package com.warehouse.service;

import com.warehouse.dto.DashboardStatsDto;
import com.warehouse.dto.LowStockItemDto;
import com.warehouse.dto.WarehouseStatsDto;
import com.warehouse.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for dashboard operations with optimized queries and caching.
 * Reduces database load by using aggregated queries instead of loading all entities.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;

    @Autowired
    public DashboardService(StockRepository stockRepository,
                           WarehouseRepository warehouseRepository,
                           ProductRepository productRepository,
                           CategoryRepository categoryRepository,
                           BrandRepository brandRepository,
                           ColorRepository colorRepository) {
        this.stockRepository = stockRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.colorRepository = colorRepository;
    }

    /**
     * Get all dashboard statistics in a single optimized call.
     * Cached for 5 minutes to reduce database load.
     */
    @Cacheable(value = "dashboardStats", unless = "#result == null")
    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        // Get stock aggregates with a single query
        StockRepository.StockAggregateResult aggregates = stockRepository.getStockAggregates();
        stats.setTotalStockQuantity(aggregates.getTotalQuantity() != null ? aggregates.getTotalQuantity() : 0L);
        stats.setTotalReserved(aggregates.getTotalReserved() != null ? aggregates.getTotalReserved() : 0L);
        stats.setTotalConsigned(aggregates.getTotalConsigned() != null ? aggregates.getTotalConsigned() : 0L);
        stats.setTotalStockValue(aggregates.getTotalValue() != null ? aggregates.getTotalValue() : java.math.BigDecimal.ZERO);

        // Get counts using efficient count queries
        stats.setTotalWarehouses(warehouseRepository.count());
        stats.setActiveWarehouses((long) warehouseRepository.findAllActive().size());
        stats.setTotalProducts(productRepository.count());
        stats.setTotalCategories(categoryRepository.count());
        stats.setTotalBrands(brandRepository.count());
        stats.setTotalColors(colorRepository.count());
        stats.setLowStockItems(stockRepository.countLowStockItems());
        stats.setOutOfStockItems(stockRepository.countOutOfStockItems());

        return stats;
    }

    /**
     * Get warehouse-level statistics with aggregated data.
     * Cached for 5 minutes to reduce database load.
     */
    @Cacheable(value = "warehouseStats", unless = "#result == null || #result.isEmpty()")
    public List<WarehouseStatsDto> getWarehouseStats() {
        List<StockRepository.WarehouseStatsResult> results = stockRepository.getWarehouseStats();
        
        return results.stream()
            .map(result -> {
                WarehouseStatsDto dto = new WarehouseStatsDto();
                dto.setId(result.getWarehouseId());
                dto.setName(result.getWarehouseName());
                dto.setLocation(result.getWarehouseLocation());
                dto.setTotalQuantity(result.getTotalQuantity() != null ? result.getTotalQuantity() : 0L);
                dto.setReserved(result.getReserved() != null ? result.getReserved() : 0L);
                dto.setConsigned(result.getConsigned() != null ? result.getConsigned() : 0L);
                dto.setProductCount(result.getProductCount() != null ? result.getProductCount() : 0L);
                dto.setTotalValue(result.getTotalValue() != null ? result.getTotalValue() : java.math.BigDecimal.ZERO);
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Get low stock items with minimal data (first 50 items).
     * Cached for 3 minutes.
     */
    @Cacheable(value = "lowStockItems", unless = "#result == null || #result.isEmpty()")
    public List<LowStockItemDto> getLowStockItems() {
        List<StockRepository.LowStockItemResult> results = stockRepository.getLowStockItemsForDashboard();
        
        return results.stream()
            .map(result -> new LowStockItemDto(
                result.getStockId(),
                result.getProductId(),
                result.getProductName(),
                result.getProductSku(),
                result.getWarehouseId(),
                result.getWarehouseName(),
                result.getQuantity(),
                result.getMinStockLevel(),
                result.getBrandId(),
                result.getColorId()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Get out of stock items with minimal data (first 50 items).
     * Cached for 3 minutes.
     */
    @Cacheable(value = "outOfStockItems", unless = "#result == null || #result.isEmpty()")
    public List<LowStockItemDto> getOutOfStockItems() {
        List<StockRepository.OutOfStockItemResult> results = stockRepository.getOutOfStockItemsForDashboard();
        
        return results.stream()
            .map(result -> {
                LowStockItemDto dto = new LowStockItemDto();
                dto.setStockId(result.getStockId());
                dto.setProductId(result.getProductId());
                dto.setProductName(result.getProductName());
                dto.setProductSku(result.getProductSku());
                dto.setWarehouseId(result.getWarehouseId());
                dto.setWarehouseName(result.getWarehouseName());
                dto.setQuantity(result.getQuantity());
                dto.setBrandId(result.getBrandId());
                dto.setColorId(result.getColorId());
                return dto;
            })
            .collect(Collectors.toList());
    }
}
