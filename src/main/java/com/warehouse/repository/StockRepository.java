package com.warehouse.repository;

import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Override
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findAll();

    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Stock> findByProductAndWarehouse(Product product, Warehouse warehouse);

    @Query("SELECT s FROM Stock s WHERE s.product = :product AND s.warehouse = :warehouse AND (:customerName IS NULL OR s.customerName = :customerName)")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Stock> findByProductAndWarehouseAndCustomerName(@Param("product") Product product, 
                                                               @Param("warehouse") Warehouse warehouse,
                                                               @Param("customerName") String customerName);

    @Query("SELECT s FROM Stock s WHERE s.product = :product ORDER BY s.warehouse.name")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findByProduct(@Param("product") Product product);

    @Query("SELECT s FROM Stock s WHERE s.warehouse = :warehouse ORDER BY s.product.name")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT s FROM Stock s WHERE s.quantity > 0 AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 2 ELSE s.minStockLevel END)")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findLowStockItems();

    @Query(value = "SELECT COUNT(*) FROM stocks WHERE quantity > 0 AND quantity <= CASE WHEN min_stock_level IS NULL OR min_stock_level = 0 THEN 2 ELSE min_stock_level END", nativeQuery = true)
    long countLowStockItems();

    @Query("SELECT s FROM Stock s WHERE s.quantity = 0")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findOutOfStockItems();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity = 0")
    long countOutOfStockItems();

    @Query("SELECT s FROM Stock s WHERE s.warehouse = :warehouse AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 2 ELSE s.minStockLevel END)")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findLowStockItemsByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT SUM(s.quantity) FROM Stock s WHERE s.product = :product")
    Long getTotalQuantityByProduct(@Param("product") Product product);

    @Query("SELECT SUM(s.quantity) FROM Stock s WHERE s.warehouse = :warehouse")
    Long getTotalQuantityByWarehouse(@Param("warehouse") Warehouse warehouse);

    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT s FROM Stock s
        JOIN s.product p
        JOIN p.category c
        LEFT JOIN c.parent cp
        WHERE (:brandId IS NULL OR p.brand.id = :brandId)
          AND (:colorId IS NULL OR p.color.id = :colorId)
          AND (:warehouseId IS NULL OR s.warehouse.id = :warehouseId)
          AND (COALESCE(:hasWarehouseFilter, false) = false OR s.warehouse.id IN :warehouseIds)
          AND (
                :categoryId IS NULL
                OR c.id = :categoryId
                OR (cp IS NOT NULL AND cp.id = :categoryId)
          )
          AND (:subCategoryId IS NULL OR c.id = :subCategoryId)
          AND (:searchEnabled = false OR (
                LOWER(p.name) LIKE :searchPattern
             OR LOWER(p.sku) LIKE :searchPattern
             OR LOWER(s.warehouse.name) LIKE :searchPattern
             OR LOWER(COALESCE(s.warehouse.location, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.additionNote, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.customerName, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.customerPhone, '')) LIKE :searchPattern))
          AND (:reservedOnly = false OR COALESCE(s.reservedQuantity, 0) > 0)
          AND (:consignedOnly = false OR COALESCE(s.consignedQuantity, 0) > 0)
          AND (:hideOutOfStock = false OR s.quantity > 0)
          AND (
                :status = 'ALL'
                OR (:status = 'LOW' AND s.quantity > 0 AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 2 ELSE s.minStockLevel END))
                OR (:status = 'OUT' AND s.quantity = 0)
          )
          AND s.lastUpdated >= :lastUpdatedFrom
          AND s.lastUpdated <= :lastUpdatedTo
    """)
    Page<Stock> findByFilters(@Param("brandId") Long brandId,
                              @Param("colorId") Long colorId,
                              @Param("warehouseId") Long warehouseId,
                              @Param("warehouseIds") java.util.List<Long> warehouseIds,
                              @Param("hasWarehouseFilter") Boolean hasWarehouseFilter,
                              @Param("categoryId") Long categoryId,
                              @Param("subCategoryId") Long subCategoryId,
                              @Param("searchEnabled") boolean searchEnabled,
                              @Param("searchPattern") String searchPattern,
                              @Param("reservedOnly") boolean reservedOnly,
                              @Param("consignedOnly") boolean consignedOnly,
                              @Param("hideOutOfStock") boolean hideOutOfStock,
                              @Param("status") String status,
                              @Param("lastUpdatedFrom") java.time.LocalDateTime lastUpdatedFrom,
                              @Param("lastUpdatedTo") java.time.LocalDateTime lastUpdatedTo,
                              Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(s.quantity), 0) FROM Stock s
        JOIN s.product p
        JOIN p.category c
        LEFT JOIN c.parent cp
        WHERE (:brandId IS NULL OR p.brand.id = :brandId)
          AND (:colorId IS NULL OR p.color.id = :colorId)
          AND (:warehouseId IS NULL OR s.warehouse.id = :warehouseId)
          AND (COALESCE(:hasWarehouseFilter, false) = false OR s.warehouse.id IN :warehouseIds)
          AND (
                :categoryId IS NULL
                OR c.id = :categoryId
                OR (cp IS NOT NULL AND cp.id = :categoryId)
          )
          AND (:subCategoryId IS NULL OR c.id = :subCategoryId)
          AND (:searchEnabled = false OR (
                LOWER(p.name) LIKE :searchPattern
             OR LOWER(p.sku) LIKE :searchPattern
             OR LOWER(s.warehouse.name) LIKE :searchPattern
             OR LOWER(COALESCE(s.warehouse.location, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.additionNote, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.customerName, '')) LIKE :searchPattern
             OR LOWER(COALESCE(s.customerPhone, '')) LIKE :searchPattern))
          AND (:reservedOnly = false OR COALESCE(s.reservedQuantity, 0) > 0)
          AND (:consignedOnly = false OR COALESCE(s.consignedQuantity, 0) > 0)
          AND (:hideOutOfStock = false OR s.quantity > 0)
          AND (
                :status = 'ALL'
                OR (:status = 'LOW' AND s.quantity > 0 AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 2 ELSE s.minStockLevel END))
                OR (:status = 'OUT' AND s.quantity = 0)
          )
          AND s.lastUpdated >= :lastUpdatedFrom
          AND s.lastUpdated <= :lastUpdatedTo
    """)
    Long sumQuantityByFilters(@Param("brandId") Long brandId,
                              @Param("colorId") Long colorId,
                              @Param("warehouseId") Long warehouseId,
                              @Param("warehouseIds") java.util.List<Long> warehouseIds,
                              @Param("hasWarehouseFilter") Boolean hasWarehouseFilter,
                              @Param("categoryId") Long categoryId,
                              @Param("subCategoryId") Long subCategoryId,
                              @Param("searchEnabled") boolean searchEnabled,
                              @Param("searchPattern") String searchPattern,
                              @Param("reservedOnly") boolean reservedOnly,
                              @Param("consignedOnly") boolean consignedOnly,
                              @Param("hideOutOfStock") boolean hideOutOfStock,
                              @Param("status") String status,
                              @Param("lastUpdatedFrom") java.time.LocalDateTime lastUpdatedFrom,
                              @Param("lastUpdatedTo") java.time.LocalDateTime lastUpdatedTo);

    @Query("""
        SELECT s FROM Stock s
        WHERE s.warehouse = :warehouse
          AND s.product.id IN :productIds
    """)
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findByWarehouseAndProductIds(@Param("warehouse") Warehouse warehouse,
                                             @Param("productIds") Collection<Long> productIds);

    @Query("""
        SELECT s.product.id AS productId, SUM(s.quantity) AS totalQuantity
        FROM Stock s
        WHERE s.product.id IN :productIds
        GROUP BY s.product.id
    """)
    List<ProductQuantityAggregate> getTotalQuantitiesByProductIds(@Param("productIds") Collection<Long> productIds);

    interface ProductQuantityAggregate {
        Long getProductId();
        Long getTotalQuantity();
    }

    @Query("""
        SELECT s.warehouse.id AS warehouseId, COALESCE(SUM(s.quantity), 0) AS totalQuantity
        FROM Stock s
        WHERE s.warehouse.id IN :warehouseIds
        GROUP BY s.warehouse.id
    """)
    List<WarehouseQuantityAggregate> getTotalQuantitiesByWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);

    interface WarehouseQuantityAggregate {
        Long getWarehouseId();
        Long getTotalQuantity();
    }

    // ============================================================================
    // Dashboard Optimized Queries
    // ============================================================================

    /**
     * Get aggregated stock statistics for dashboard.
     * Returns: totalStockQuantity, totalReserved, totalConsigned, totalStockValue
     */
    @Query(value = """
        SELECT 
            COALESCE(SUM(s.quantity), 0) as totalQuantity,
            COALESCE(SUM(s.reserved_quantity), 0) as totalReserved,
            COALESCE(SUM(s.consigned_quantity), 0) as totalConsigned,
            COALESCE(SUM(s.quantity * p.price), 0) as totalValue
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        """, nativeQuery = true)
    StockAggregateResult getStockAggregates();

    interface StockAggregateResult {
        Long getTotalQuantity();
        Long getTotalReserved();
        Long getTotalConsigned();
        java.math.BigDecimal getTotalValue();
    }

    /**
     * Get warehouse-level aggregated statistics for dashboard.
     */
    @Query(value = """
        SELECT 
            w.id as warehouseId,
            w.name as warehouseName,
            w.location as warehouseLocation,
            COALESCE(SUM(s.quantity), 0) as totalQuantity,
            COALESCE(SUM(s.reserved_quantity), 0) as reserved,
            COALESCE(SUM(s.consigned_quantity), 0) as consigned,
            COUNT(DISTINCT s.product_id) as productCount,
            COALESCE(SUM(s.quantity * p.price), 0) as totalValue
        FROM warehouses w
        LEFT JOIN stocks s ON w.id = s.warehouse_id
        LEFT JOIN products p ON s.product_id = p.id
        GROUP BY w.id, w.name, w.location
        ORDER BY w.name
        """, nativeQuery = true)
    List<WarehouseStatsResult> getWarehouseStats();

    interface WarehouseStatsResult {
        Long getWarehouseId();
        String getWarehouseName();
        String getWarehouseLocation();
        Long getTotalQuantity();
        Long getReserved();
        Long getConsigned();
        Long getProductCount();
        java.math.BigDecimal getTotalValue();
    }

    /**
     * Get low stock items with minimal data for dashboard display.
     * Only returns first 50 items for performance.
     */
    @Query(value = """
        SELECT 
            s.id as stockId,
            p.id as productId,
            p.name as productName,
            p.sku as productSku,
            w.id as warehouseId,
            w.name as warehouseName,
            s.quantity as quantity,
            s.min_stock_level as minStockLevel,
            p.brand_id as brandId,
            p.color_id as colorId
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        INNER JOIN warehouses w ON s.warehouse_id = w.id
        WHERE s.quantity > 0 
          AND s.quantity <= CASE 
              WHEN s.min_stock_level IS NULL OR s.min_stock_level = 0 THEN 2 
              ELSE s.min_stock_level 
          END
        ORDER BY s.quantity ASC, p.name ASC
        LIMIT 50
        """, nativeQuery = true)
    List<LowStockItemResult> getLowStockItemsForDashboard();

    interface LowStockItemResult {
        Long getStockId();
        Long getProductId();
        String getProductName();
        String getProductSku();
        Long getWarehouseId();
        String getWarehouseName();
        Integer getQuantity();
        Integer getMinStockLevel();
        Long getBrandId();
        Long getColorId();
    }

    /**
     * Get out of stock items with minimal data for dashboard display.
     * Only returns first 50 items for performance.
     */
    @Query(value = """
        SELECT 
            s.id as stockId,
            p.id as productId,
            p.name as productName,
            p.sku as productSku,
            w.id as warehouseId,
            w.name as warehouseName,
            s.quantity as quantity,
            p.brand_id as brandId,
            p.color_id as colorId
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        INNER JOIN warehouses w ON s.warehouse_id = w.id
        WHERE s.quantity = 0
        ORDER BY p.name ASC
        LIMIT 50
        """, nativeQuery = true)
    List<OutOfStockItemResult> getOutOfStockItemsForDashboard();

    interface OutOfStockItemResult {
        Long getStockId();
        Long getProductId();
        String getProductName();
        String getProductSku();
        Long getWarehouseId();
        String getWarehouseName();
        Integer getQuantity();
        Long getBrandId();
        Long getColorId();
    }

    /**
     * Get aggregated stock statistics with filters for dashboard.
     * Note: warehouseIds should be null if no warehouse filter is applied.
     */
    @Query(value = """
        SELECT 
            COALESCE(SUM(s.quantity), 0) as totalQuantity,
            COALESCE(SUM(s.reserved_quantity), 0) as totalReserved,
            COALESCE(SUM(s.consigned_quantity), 0) as totalConsigned,
            COALESCE(SUM(s.quantity * p.price), 0) as totalValue,
            COUNT(DISTINCT p.id) as productCount
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        WHERE (:brandId IS NULL OR p.brand_id = :brandId)
          AND (:colorId IS NULL OR p.color_id = :colorId)
          AND (COALESCE(:hasWarehouseFilter, false) = false OR s.warehouse_id IN (:warehouseIds))
        """, nativeQuery = true)
    FilteredStockAggregateResult getFilteredStockAggregates(
            @Param("brandId") Long brandId,
            @Param("colorId") Long colorId,
            @Param("warehouseIds") java.util.List<Long> warehouseIds,
            @Param("hasWarehouseFilter") Boolean hasWarehouseFilter);

    interface FilteredStockAggregateResult {
        Long getTotalQuantity();
        Long getTotalReserved();
        Long getTotalConsigned();
        java.math.BigDecimal getTotalValue();
        Long getProductCount();
    }

    /**
     * Count low stock items with filters.
     */
    @Query(value = """
        SELECT COUNT(*) 
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        WHERE s.quantity > 0 
          AND s.quantity <= CASE WHEN s.min_stock_level IS NULL OR s.min_stock_level = 0 THEN 2 ELSE s.min_stock_level END
          AND (:brandId IS NULL OR p.brand_id = :brandId)
          AND (:colorId IS NULL OR p.color_id = :colorId)
          AND (COALESCE(:hasWarehouseFilter, false) = false OR s.warehouse_id IN (:warehouseIds))
        """, nativeQuery = true)
    long countFilteredLowStockItems(
            @Param("brandId") Long brandId,
            @Param("colorId") Long colorId,
            @Param("warehouseIds") java.util.List<Long> warehouseIds,
            @Param("hasWarehouseFilter") Boolean hasWarehouseFilter);

    /**
     * Count out of stock items with filters.
     */
    @Query(value = """
        SELECT COUNT(*) 
        FROM stocks s
        INNER JOIN products p ON s.product_id = p.id
        WHERE s.quantity = 0
          AND (:brandId IS NULL OR p.brand_id = :brandId)
          AND (:colorId IS NULL OR p.color_id = :colorId)
          AND (COALESCE(:hasWarehouseFilter, false) = false OR s.warehouse_id IN (:warehouseIds))
        """, nativeQuery = true)
    long countFilteredOutOfStockItems(
            @Param("brandId") Long brandId,
            @Param("colorId") Long colorId,
            @Param("warehouseIds") java.util.List<Long> warehouseIds,
            @Param("hasWarehouseFilter") Boolean hasWarehouseFilter);
}
