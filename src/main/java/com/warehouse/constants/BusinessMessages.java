package com.warehouse.constants;

/**
 * Business-layer reusable message templates.
 */
public final class BusinessMessages {

    private BusinessMessages() {}

    public static final String ID_PREFIX = "ID: ";
    public static final String SKU_PREFIX = "SKU: ";

    public static final String REQUIRED_MODE_VALUE_DIRECTION = "Mode, value and direction are required";
    public static final String INVALID_MODE = "Invalid mode";
    public static final String INVALID_DIRECTION = "Invalid direction";
    public static final String VALUE_MUST_BE_POSITIVE = "Value must be positive";
    public static final String PERCENTAGE_TOO_HIGH = "Percentage is unrealistically high";
    public static final String NO_PRODUCTS_FOR_BULK = "No products found matching bulk price update criteria";

    public static final String CATEGORY_CANNOT_BE_ITS_OWN_PARENT = "Category cannot be its own parent";
    public static final String CATEGORY_DESCENDANT_CANNOT_BE_PARENT = "Cannot set a descendant as parent";

    public static final String INVALID_CREDENTIALS = "Invalid credentials";

    public static final String USERNAME_ALREADY_EXISTS = "Username already exists";
    public static final String USER_NOT_FOUND = "User not found";
}


