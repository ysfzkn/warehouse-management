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
        ValidationUtil.requireNonNull(product.getCategory(), "Kategori");
        ValidationUtil.requireNonNull(product.getCategory().getId(), "Kategori ID");
    }

    public static void validateStockForCreation(Stock stock) {
        ValidationUtil.requireNonNull(stock.getProduct(), "Ürün");
        ValidationUtil.requireNonNull(stock.getProduct().getId(), "Ürün ID");
        ValidationUtil.requireNonNull(stock.getWarehouse(), "Depo");
        ValidationUtil.requireNonNull(stock.getWarehouse().getId(), "Depo ID");
    }

    public static void validateWarehousesDifferent(Warehouse source, Warehouse destination) {
        if (source.getId().equals(destination.getId())) {
            throw new WarehouseManagementException(ErrorCode.SAME_SOURCE_DESTINATION);
        }
    }

    public static void validateEntityHasNoRelations(boolean hasRelations, String entityName, String relationType) {
        if (hasRelations) {
            ErrorCode errorCode;
            switch (relationType) {
                case "stocks":
                    errorCode = ErrorCode.CANNOT_DELETE_WITH_STOCKS;
                    break;
                case "products":
                    errorCode = ErrorCode.CANNOT_DELETE_WITH_PRODUCTS;
                    break;
                case "subcategories":
                    errorCode = ErrorCode.CANNOT_DELETE_WITH_SUBCATEGORIES;
                    break;
                default:
                    errorCode = ErrorCode.CANNOT_DELETE_WITH_PRODUCTS;
            }
            throw new WarehouseManagementException(errorCode);
        }
    }
}
