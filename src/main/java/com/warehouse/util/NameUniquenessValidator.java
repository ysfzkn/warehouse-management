package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Utility class for validating name uniqueness across entities.
 */
public final class NameUniquenessValidator {

    private static final Logger logger = LoggerFactory.getLogger(NameUniquenessValidator.class);

    private NameUniquenessValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates that a name is unique for creation.
     *
     * @param name the name to validate
     * @param nameExistsFunction function that checks if name exists
     * @param errorCode the error code to throw if name exists
     * @param entityType the type of entity for logging
     */
    public static void validateNameUniqueness(String name, 
                                             Function<String, Boolean> nameExistsFunction,
                                             ErrorCode errorCode,
                                             String entityType) {
        if (nameExistsFunction.apply(name)) {
            logger.warn("{} name already exists: {}", entityType, name);
            throw new WarehouseManagementException(errorCode, "Name: " + name);
        }
    }

    /**
     * Validates that a name is unique for update.
     *
     * @param currentName the current name
     * @param newName the new name
     * @param nameExistsFunction function that checks if name exists
     * @param errorCode the error code to throw if name exists
     * @param entityType the type of entity for logging
     */
    public static void validateNameUniquenessOnUpdate(String currentName,
                                                      String newName,
                                                      Function<String, Boolean> nameExistsFunction,
                                                      ErrorCode errorCode,
                                                      String entityType) {
        if (!currentName.equals(newName) && nameExistsFunction.apply(newName)) {
            logger.warn("{} name already exists for update: {}", entityType, newName);
            throw new WarehouseManagementException(errorCode, "Name: " + newName);
        }
    }
}

