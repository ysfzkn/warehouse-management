package com.warehouse.repository;

import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductAndWarehouse(Product product, Warehouse warehouse);

    @Query("SELECT s FROM Stock s WHERE s.product = :product ORDER BY s.warehouse.name")
    List<Stock> findByProduct(@Param("product") Product product);

    @Query("SELECT s FROM Stock s WHERE s.warehouse = :warehouse ORDER BY s.product.name")
    List<Stock> findByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT s FROM Stock s WHERE s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
    List<Stock> findLowStockItems();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
    long countLowStockItems();

    @Query("SELECT s FROM Stock s WHERE s.quantity = 0")
    List<Stock> findOutOfStockItems();

    @Query("SELECT s FROM Stock s WHERE s.warehouse = :warehouse AND s.quantity <= (CASE WHEN s.minStockLevel IS NULL OR s.minStockLevel = 0 THEN 10 ELSE s.minStockLevel END)")
    List<Stock> findLowStockItemsByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT SUM(s.quantity) FROM Stock s WHERE s.product = :product")
    Long getTotalQuantityByProduct(@Param("product") Product product);

    @Query("SELECT SUM(s.quantity) FROM Stock s WHERE s.warehouse = :warehouse")
    Long getTotalQuantityByWarehouse(@Param("warehouse") Warehouse warehouse);
}
