package com.warehouse.util;

import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

public final class EntityValidator {

    private EntityValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void validateProductForCreation(Product product) {
        ValidationUtil.requireNonNull(product.getCategory(), "Category");
        ValidationUtil.requireNonNull(product.getCategory().getId(), "Category ID");
    }

    public static void validateStockForCreation(Stock stock) {
        ValidationUtil.requireNonNull(stock.getProduct(), "Product");
        ValidationUtil.requireNonNull(stock.getProduct().getId(), "Product ID");
        ValidationUtil.requireNonNull(stock.getWarehouse(), "Warehouse");
        ValidationUtil.requireNonNull(stock.getWarehouse().getId(), "Warehouse ID");
    }

    public static void validateWarehousesDifferent(Warehouse source, Warehouse destination) {
        if (source.getId().equals(destination.getId())) {
            throw new WarehouseManagementException(ErrorCode.SAME_SOURCE_DESTINATION);
        }
    }

    public static void validateEntityHasNoRelations(boolean hasRelations, String entityName, String relationType) {
        if (hasRelations) {
            ErrorCode errorCode = relationType.equals("stocks") 
                ? ErrorCode.CANNOT_DELETE_WITH_STOCKS 
                : ErrorCode.CANNOT_DELETE_WITH_PRODUCTS;
            throw new WarehouseManagementException(errorCode, entityName + " has existing " + relationType);
        }
    }
}
