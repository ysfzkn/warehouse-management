package com.warehouse.constants;

/**
 * Constants for stock import messages and statuses.
 */
public final class ImportMessages {

    private ImportMessages() {}

    public static final String STORAGE_DIR = "uploads/stock-imports";
    public static final String TEMPLATE_SHEET_NAME = "Stok Şablonu";

    public static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_TR_SUCCESS = "BAŞARILI";
    public static final String STATUS_TR_FAILED = "BAŞARISIZ";
    public static final String STATUS_TR_PARTIAL = "KISMEN";

    public static final String MISSING_REQUIRED_PREFIX = "Eksik zorunlu alanlar: ";
    public static final String NO_ROWS_PROCESSED = "İşlenen satır bulunamadı";
    public static final String SKIPPED_ROWS_TEMPLATE = "%d satır atlandı (eksik zorunlu alanlar veya hatalar)";
    public static final String ERROR_PREFIX = "Hata: ";
}


