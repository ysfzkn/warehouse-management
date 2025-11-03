package com.warehouse.constants;

/**
 * Constants for stock import messages and statuses.
 */
public final class ImportMessages {

    private ImportMessages() {}

    public static final String STORAGE_DIR = "uploads/stock-imports";
    public static final String TEMPLATE_SHEET_NAME = "Stock Template";

    public static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_TR_SUCCESS = "BAŞARILI";
    public static final String STATUS_TR_FAILED = "BAŞARISIZ";
    public static final String STATUS_TR_PARTIAL = "KISMEN";

    public static final String MISSING_REQUIRED_PREFIX = "Missing required fields: ";
    public static final String NO_ROWS_PROCESSED = "No rows were processed";
    public static final String SKIPPED_ROWS_TEMPLATE = "%d rows were skipped (missing required fields or errors)";
    public static final String ERROR_PREFIX = "Error: ";
}


