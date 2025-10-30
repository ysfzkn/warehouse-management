package com.warehouse.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    
    // Resource Not Found Errors (404)
    PRODUCT_NOT_FOUND("PRODUCT_001", "Product not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND("CATEGORY_001", "Category not found", HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_FOUND("WAREHOUSE_001", "Warehouse not found", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND("BRAND_001", "Brand not found", HttpStatus.NOT_FOUND),
    COLOR_NOT_FOUND("COLOR_001", "Color not found", HttpStatus.NOT_FOUND),
    STOCK_NOT_FOUND("STOCK_001", "Stock not found", HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND("TRANSFER_001", "Transfer not found", HttpStatus.NOT_FOUND),
    
    // Duplicate Resource Errors (409)
    PRODUCT_SKU_ALREADY_EXISTS("PRODUCT_002", "Product with this SKU already exists", HttpStatus.CONFLICT),
    CATEGORY_NAME_ALREADY_EXISTS("CATEGORY_002", "Category with this name already exists", HttpStatus.CONFLICT),
    WAREHOUSE_NAME_ALREADY_EXISTS("WAREHOUSE_002", "Warehouse with this name already exists", HttpStatus.CONFLICT),
    BRAND_NAME_ALREADY_EXISTS("BRAND_002", "Brand with this name already exists", HttpStatus.CONFLICT),
    COLOR_NAME_ALREADY_EXISTS("COLOR_002", "Color with this name already exists", HttpStatus.CONFLICT),
    STOCK_ALREADY_EXISTS("STOCK_002", "Stock already exists for this product in the warehouse", HttpStatus.CONFLICT),
    
    // Business Validation Errors (400)
    REQUIRED_FIELD_MISSING("VALIDATION_001", "Required field is missing", HttpStatus.BAD_REQUEST),
    INVALID_VALUE("VALIDATION_002", "Invalid value provided", HttpStatus.BAD_REQUEST),
    VALUE_MUST_BE_POSITIVE("VALIDATION_003", "Value must be positive", HttpStatus.BAD_REQUEST),
    VALUE_CANNOT_BE_NEGATIVE("VALIDATION_004", "Value cannot be negative", HttpStatus.BAD_REQUEST),
    
    // Stock Related Errors (400)
    INSUFFICIENT_STOCK("STOCK_003", "Insufficient stock available", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_RESERVED_STOCK("STOCK_004", "Cannot release more than reserved quantity", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_IN_WAREHOUSE("STOCK_005", "Product not found in warehouse", HttpStatus.BAD_REQUEST),
    
    // Transfer Related Errors (400)
    SAME_SOURCE_DESTINATION("TRANSFER_002", "Source and destination warehouses must be different", HttpStatus.BAD_REQUEST),
    INVALID_TRANSFER_STATUS("TRANSFER_003", "Invalid transfer status for this operation", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_COMPLETED("TRANSFER_004", "Transfer is already completed", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_CANCELLED("TRANSFER_005", "Transfer is already cancelled", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_COMPLETED("TRANSFER_006", "Cannot cancel a completed transfer", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_IN_TRANSIT("TRANSFER_007", "Cannot delete a transfer that is in transit", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_COMPLETED("TRANSFER_008", "Cannot delete a completed transfer", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_UPDATED("TRANSFER_009", "Only pending transfers can be updated", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_STARTED("TRANSFER_010", "Only pending transfers can be started", HttpStatus.BAD_REQUEST),
    
    // Relationship Errors (400)
    CANNOT_DELETE_WITH_STOCKS("RELATION_001", "Cannot delete entity with existing stocks", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_WITH_PRODUCTS("RELATION_002", "Cannot delete entity with existing products", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_WITH_SUBCATEGORIES("RELATION_003", "Cannot delete entity with existing subcategories", HttpStatus.BAD_REQUEST),
    CATEGORY_INVALID_PARENT("CATEGORY_003", "Invalid parent category", HttpStatus.BAD_REQUEST),
    
    // General Errors (500)
    INTERNAL_SERVER_ERROR("SYSTEM_001", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

