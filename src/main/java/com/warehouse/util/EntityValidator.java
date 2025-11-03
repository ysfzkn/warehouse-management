package com.warehouse.util;

import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.constants.ErrorMessages;

/**
 * Utility class for entity validation operations.
 */
public final class EntityValidator {

    private EntityValidator() {
        throw new UnsupportedOperationException(ErrorMessages.UTIL_CLASS_INSTANTIATION);
    }

    /**
     * Validates product entity for creation.
     *
     * @param product the product to validate
     * @throws WarehouseManagementException if validation fails
     */
    public static void validateProductForCreation(Product product) {
        ValidationUtil.requireNonNull(product.getCategory(), "Category");
        ValidationUtil.requireNonNull(product.getCategory().getId(), "Category ID");
    }

    /**
     * Validates stock entity for creation.
     *
     * @param stock the stock to validate
     * @throws WarehouseManagementException if validation fails
     */
    public static void validateStockForCreation(Stock stock) {
        ValidationUtil.requireNonNull(stock.getProduct(), "Product");
        ValidationUtil.requireNonNull(stock.getProduct().getId(), "Product ID");
        ValidationUtil.requireNonNull(stock.getWarehouse(), "Warehouse");
        ValidationUtil.requireNonNull(stock.getWarehouse().getId(), "Warehouse ID");
    }

    /**
     * Validates that source and destination warehouses are different.
     *
     * @param source the source warehouse
     * @param destination the destination warehouse
     * @throws WarehouseManagementException if warehouses are the same
     */
    public static void validateWarehousesDifferent(Warehouse source, Warehouse destination) {
        if (source.getId().equals(destination.getId())) {
            throw new WarehouseManagementException(ErrorCode.SAME_SOURCE_DESTINATION);
        }
    }

    /**
     * Validates that an entity has no relations before deletion.
     *
     * @param hasRelations true if entity has relations
     * @param entityName the name of the entity
     * @param relationType the type of relation
     * @throws WarehouseManagementException if entity has relations
     */
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
