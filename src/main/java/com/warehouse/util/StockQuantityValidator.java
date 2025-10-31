package com.warehouse.util;

import com.warehouse.entity.Stock;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

/**
 * Utility class for stock quantity validation operations.
 */
public final class StockQuantityValidator {

    private StockQuantityValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates that available stock quantity is sufficient.
     *
     * @param stock the stock to validate
     * @param requestedQuantity the requested quantity
     * @throws WarehouseManagementException if stock is insufficient
     */
    public static void validateSufficientStock(Stock stock, Integer requestedQuantity) {
        Integer availableQuantity = stock.getAvailableQuantity();
        if (availableQuantity < requestedQuantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK,
                String.format("Available: %d, Requested: %d", availableQuantity, requestedQuantity));
        }
    }

    /**
     * Validates that reserved stock quantity is sufficient.
     *
     * @param stock the stock to validate
     * @param requestedQuantity the requested quantity to release
     * @throws WarehouseManagementException if reserved stock is insufficient
     */
    public static void validateSufficientReservedStock(Stock stock, Integer requestedQuantity) {
        Integer reservedQuantity = stock.getReservedQuantity();
        if (reservedQuantity == null || reservedQuantity < requestedQuantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_RESERVED_STOCK);
        }
    }

    /**
     * Validates that available quantity is not negative after update.
     *
     * @param stock the stock to validate
     * @throws WarehouseManagementException if available quantity would be negative
     */
    public static void validateAvailableQuantity(Stock stock) {
        int quantity = stock.getQuantity() != null ? stock.getQuantity() : 0;
        int reserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : 0;
        int consigned = stock.getConsignedQuantity() != null ? stock.getConsignedQuantity() : 0;
        int available = quantity - reserved - consigned;
        
        if (available < 0) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}

