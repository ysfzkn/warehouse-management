package com.warehouse.service;

import com.warehouse.dto.StockFilter;
import com.warehouse.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing stock operations.
 */
public interface StockService {

    List<Stock> getAllStocks();

    Page<Stock> getAllStocks(Pageable pageable);

    Page<Stock> getStocks(StockFilter filter, Pageable pageable);

    Optional<Stock> getStockById(Long id);

    Stock getStockByIdOrThrow(Long id);

    List<Stock> getStocksByProduct(Long productId);

    List<Stock> getStocksByWarehouse(Long warehouseId);

    Optional<Stock> getStockByProductAndWarehouse(Long productId, Long warehouseId);

    List<Stock> getLowStockItems();

    List<Stock> getOutOfStockItems();

    List<Stock> getLowStockItemsByWarehouse(Long warehouseId);

    /**
     * Returns the total number of low stock items using an efficient count query.
     */
    long countLowStockItems();

    Long getTotalQuantityByProduct(Long productId);

    Long getTotalQuantityByWarehouse(Long warehouseId);

    Stock createStock(Stock stock);

    List<Stock> createStocks(List<Stock> stocks);

    Stock updateStock(Long id, Stock stockDetails);

    Stock addToStock(Long stockId, Integer quantity);

    Stock removeFromStock(Long stockId, Integer quantity);

    void deleteStock(Long id);

    void deleteStocks(List<Long> ids);

    Stock reserveStock(Long stockId, Integer quantity);

    Stock releaseStock(Long stockId, Integer quantity);
}
