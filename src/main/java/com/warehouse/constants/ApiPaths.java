package com.warehouse.constants;

/**
 * Centralized API path constants to avoid scattering literal strings.
 */
public final class ApiPaths {

    private ApiPaths() {}

    public static final String API_BASE = "/api";
    public static final String AUTH = API_BASE + "/auth/**";
    public static final String ACTUATOR = "/actuator/**";
    public static final String INFO = API_BASE + "/info";
    public static final String ERROR = "/error";
    public static final String STREAM = API_BASE + "/stream";
    public static final String STOCKS = API_BASE + "/stocks/**";
    public static final String STOCK_TRANSFERS = API_BASE + "/stock-transfers/**";
    public static final String PRODUCTS = API_BASE + "/products/**";
    public static final String WAREHOUSES = API_BASE + "/warehouses/**";
    public static final String CATEGORIES = API_BASE + "/categories/**";
    public static final String BRANDS = API_BASE + "/brands/**";
    public static final String COLORS = API_BASE + "/colors/**";
    public static final String ANY_API = API_BASE + "/**";
}


