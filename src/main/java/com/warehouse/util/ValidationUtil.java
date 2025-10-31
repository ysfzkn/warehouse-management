package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

import java.math.BigDecimal;

/**
 * Utility class for common validation operations.
 */
public final class ValidationUtil {

    private ValidationUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates that an object is not null.
     *
     * @param object the object to validate
     * @param fieldName the name of the field for error message
     * @throws WarehouseManagementException if object is null
     */
    public static void requireNonNull(Object object, String fieldName) {
        if (object == null) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, fieldName);
        }
    }

    /**
     * Validates that an integer value is positive.
     *
     * @param value the value to validate
     * @param fieldName the name of the field for error message
     * @throws WarehouseManagementException if value is null or not positive
     */
    public static void requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, fieldName);
        }
    }

    /**
     * Validates that an integer value is non-negative.
     *
     * @param value the value to validate
     * @param fieldName the name of the field for error message
     * @throws WarehouseManagementException if value is null or negative
     */
    public static void requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_CANNOT_BE_NEGATIVE, fieldName);
        }
    }

    /**
     * Validates that a BigDecimal value is positive.
     *
     * @param value the value to validate
     * @param fieldName the name of the field for error message
     * @throws WarehouseManagementException if value is null or not positive
     */
    public static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, fieldName);
        }
    }

    /**
     * Validates that a condition is true.
     *
     * @param condition the condition to validate
     * @param errorCode the error code to throw if condition is false
     * @param message the error message
     * @throws WarehouseManagementException if condition is false
     */
    public static void requireTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new WarehouseManagementException(errorCode, message);
        }
    }

    /**
     * Validates that a string is not blank.
     *
     * @param value the string to validate
     * @param fieldName the name of the field for error message
     * @throws WarehouseManagementException if string is null or blank
     */
    public static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, fieldName);
        }
    }
}
