package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

public final class ValidationUtil {

    private ValidationUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void requireNonNull(Object object, String fieldName) {
        if (object == null) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, fieldName);
        }
    }

    public static void requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, fieldName);
        }
    }

    public static void requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_CANNOT_BE_NEGATIVE, fieldName);
        }
    }

    public static void requireTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new WarehouseManagementException(errorCode, message);
        }
    }
}
