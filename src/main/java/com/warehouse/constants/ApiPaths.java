package com.warehouse.constants;

/**
 * Centralized API path constants.
 * Admin paths serve the existing WMS dashboard.
 * Store paths serve the new B2C e-commerce storefront.
 */
public final class ApiPaths {

    private ApiPaths() {}

    // ── Base ─────────────────────────────────────────
    public static final String API_BASE = "/api";
    public static final String API_ADMIN_BASE = API_BASE + "/admin";
    public static final String API_STORE_BASE = API_BASE + "/store";

    // ── Public (no auth) ─────────────────────────────
    /**
     * Artık hiçbir controller bu yolu karşılamıyor. Sabit, yanlışlıkla yeniden
     * açılmasın diye kaldırıldı: burada duran InfoController Railway'in DATABASE_URL'ini
     * (içinde veritabanı şifresiyle birlikte) kimlik doğrulaması olmadan yayınlıyordu.
     */
    @Deprecated
    public static final String INFO = API_BASE + "/info";
    public static final String ACTUATOR = "/actuator/**";
    public static final String ERROR = "/error";

    // ── Admin paths (existing WMS) ───────────────────
    public static final String ADMIN_AUTH = API_ADMIN_BASE + "/auth/**";
    // NOTE: the WMS assistant controller is physically mounted at /api/cezeri/**
    // (preserved from v1 for frontend compatibility). The admin filter chain
    // securityMatcher explicitly includes this path so the role check below
    // actually applies — prior to Cezeri v2 this rule was dead code.
    public static final String CEZERI = "/api/cezeri/**";
    /** SSE stream plus the single-use ticket endpoint used to open it. */
    public static final String ADMIN_STREAM = API_ADMIN_BASE + "/stream/**";
    public static final String ADMIN_STOCKS = API_ADMIN_BASE + "/stocks/**";
    public static final String ADMIN_STOCK_TRANSFERS = API_ADMIN_BASE + "/stock-transfers/**";
    public static final String ADMIN_STOCK_IMPORTS = API_ADMIN_BASE + "/stock-imports/**";
    public static final String ADMIN_STOCK_REQUESTS = API_ADMIN_BASE + "/stock-requests/**";
    public static final String ADMIN_STOCK_TRANSFER_ITEMS = API_ADMIN_BASE + "/stock-transfer-items/**";
    public static final String ADMIN_PRODUCTS = API_ADMIN_BASE + "/products/**";
    public static final String ADMIN_WAREHOUSES = API_ADMIN_BASE + "/warehouses/**";
    public static final String ADMIN_CATEGORIES = API_ADMIN_BASE + "/categories/**";
    public static final String ADMIN_BRANDS = API_ADMIN_BASE + "/brands/**";
    public static final String ADMIN_COLORS = API_ADMIN_BASE + "/colors/**";
    public static final String ADMIN_CUSTOMERS = API_ADMIN_BASE + "/customers/**";
    public static final String ADMIN_DRIVERS = API_ADMIN_BASE + "/drivers/**";
    public static final String ADMIN_VEHICLES = API_ADMIN_BASE + "/vehicles/**";
    /** Exact path of the vehicle create endpoint — a plate may be registered mid-transfer. */
    public static final String ADMIN_VEHICLES_CREATE = API_ADMIN_BASE + "/vehicles";
    public static final String ADMIN_ANY = API_ADMIN_BASE + "/**";

    // ── Store paths (e-commerce) ─────────────────────
    public static final String STORE_AUTH = API_STORE_BASE + "/auth/**";
    public static final String STORE_PRODUCTS = API_STORE_BASE + "/products/**";
    public static final String STORE_CATEGORIES = API_STORE_BASE + "/categories/**";
    public static final String STORE_BRANDS = API_STORE_BASE + "/brands/**";
    public static final String STORE_COLORS = API_STORE_BASE + "/colors/**";
    public static final String STORE_PAGES = API_STORE_BASE + "/pages/**";
    public static final String STORE_CART = API_STORE_BASE + "/cart/**";
    public static final String STORE_CHECKOUT = API_STORE_BASE + "/checkout/**";
    public static final String STORE_SETTINGS = API_STORE_BASE + "/settings/**";
    public static final String STORE_PAYMENT_CALLBACK = API_STORE_BASE + "/payment/callback";
    public static final String STORE_PAYMENT_CALLBACK_POS = API_STORE_BASE + "/payment/callback/pos/**";
    public static final String STORE_PAYMENT_CALLBACK_PAYTR = API_STORE_BASE + "/payment/callback/paytr/**";
    public static final String STORE_PAYMENT_METHODS = API_STORE_BASE + "/payment/methods";
    public static final String STORE_PAYMENT_STATUS_TOKEN = API_STORE_BASE + "/payment/status-by-token";
    public static final String STORE_PAYMENT_INITIALIZE = API_STORE_BASE + "/payment/initialize";
    public static final String STORE_CARGO_PROVIDERS = API_STORE_BASE + "/checkout/cargo-providers";
    public static final String STORE_GUEST_CHECKOUT = API_STORE_BASE + "/checkout/guest-checkout";
    public static final String STORE_PUBLIC_ORDER_TRACK = API_STORE_BASE + "/public/orders/track";
    public static final String STORE_PRODUCT_NOTIFY_ME = API_STORE_BASE + "/products/*/notify-me";
    public static final String STORE_PRODUCT_TRACK_VIEW = API_STORE_BASE + "/products/*/track-view";
    public static final String STORE_PRODUCTS_BY_IDS = API_STORE_BASE + "/products/by-ids";
    public static final String STORE_NEWSLETTER = API_STORE_BASE + "/newsletter/**";
    public static final String STORE_ASSISTANT = API_STORE_BASE + "/assistant/**";
    public static final String STORE_ANY = API_STORE_BASE + "/**";

    // ── Admin assistant (RAG management + observability dashboard, Phase 2) ──
    public static final String ADMIN_ASSISTANT = API_ADMIN_BASE + "/assistant/**";

    // ── Legacy (for backward compatibility during transition) ──
    @Deprecated
    public static final String AUTH = ADMIN_AUTH;
    @Deprecated
    public static final String STOCKS = ADMIN_STOCKS;
    @Deprecated
    public static final String STOCK_TRANSFERS = ADMIN_STOCK_TRANSFERS;
    @Deprecated
    public static final String STOCK_IMPORTS = ADMIN_STOCK_IMPORTS;
    @Deprecated
    public static final String PRODUCTS = ADMIN_PRODUCTS;
    @Deprecated
    public static final String WAREHOUSES = ADMIN_WAREHOUSES;
    @Deprecated
    public static final String CATEGORIES = ADMIN_CATEGORIES;
    @Deprecated
    public static final String BRANDS = ADMIN_BRANDS;
    @Deprecated
    public static final String COLORS = ADMIN_COLORS;
    @Deprecated
    public static final String STREAM = ADMIN_STREAM;
    @Deprecated
    public static final String ANY_API = API_BASE + "/**";
}
