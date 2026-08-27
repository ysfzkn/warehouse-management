package com.warehouse.service;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.StockFilter;
import com.warehouse.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
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

    List<Stock> getStocksByWarehouseAndProductIds(Long warehouseId, List<Long> productIds);

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

    Map<Long, Long> getTotalQuantitiesByProductIds(List<Long> productIds);

    Long getTotalQuantityByFilter(StockFilter filter);

    Stock createStock(Stock stock);

    List<Stock> createStocks(List<Stock> stocks);

    Stock updateStock(Long id, Stock stockDetails);

    Stock addToStock(Long stockId, Integer quantity);

    Stock addToStock(Long stockId, Integer quantity, String note);

    /**
     * Adds stock that arrived on a waybill. The number and date are stamped onto the row inside
     * the same transaction and named in the audit entry, so "where did these units come from"
     * has an answer afterwards. A blank number leaves whatever is already recorded alone.
     */
    Stock addToStock(Long stockId, Integer quantity, String note, String irsaliyeNo,
                     java.time.LocalDate irsaliyeDate);

    /**
     * Waybills matching {@code query}, each with what it currently adds up to. A blank query
     * returns the most recently touched ones, which is what the operator wants when the delivery
     * they are entering is the one they were entering a minute ago.
     */
    java.util.List<com.warehouse.dto.IrsaliyeSummaryDto> summarizeIrsaliye(String query);

    Stock removeFromStock(Long stockId, Integer quantity);

    Stock removeFromStock(Long stockId, Integer quantity, String note);

    void deleteStock(Long id);

    BulkDeleteResponse deleteStocks(List<Long> ids);

    Stock reserveStock(Long stockId, Integer quantity);

    Stock releaseStock(Long stockId, Integer quantity);
}
