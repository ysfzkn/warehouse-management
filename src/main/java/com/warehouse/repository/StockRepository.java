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

    @Query("SELECT s FROM Stock s WHERE s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findLowStockItems();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
    long countLowStockItems();

    @Query("SELECT s FROM Stock s WHERE s.quantity = 0")
    @EntityGraph(value = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE, type = EntityGraph.EntityGraphType.LOAD)
    List<Stock> findOutOfStockItems();

    @Query("SELECT s FROM Stock s WHERE s.warehouse = :warehouse AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
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
          AND (
                :status = 'ALL'
                OR (:status = 'LOW' AND s.quantity > 0 AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END))
                OR (:status = 'OUT' AND s.quantity = 0)
          )
    """)
    Page<Stock> findByFilters(@Param("brandId") Long brandId,
                              @Param("colorId") Long colorId,
                              @Param("warehouseId") Long warehouseId,
                              @Param("categoryId") Long categoryId,
                              @Param("subCategoryId") Long subCategoryId,
                              @Param("searchEnabled") boolean searchEnabled,
                              @Param("searchPattern") String searchPattern,
                              @Param("reservedOnly") boolean reservedOnly,
                              @Param("consignedOnly") boolean consignedOnly,
                              @Param("status") String status,
                              Pageable pageable);

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
}
