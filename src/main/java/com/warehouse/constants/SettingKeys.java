package com.warehouse.constants;

/**
 * Every key stored in {@code site_settings}, in one place.
 *
 * <p>These strings are a contract between Java, Flyway migrations and the admin screen, and they
 * were being retyped at each of eighty call sites. A typo in any of them does not fail to
 * compile and does not throw: {@code getSetting} simply returns nothing, so the feature behaves
 * as though the administrator never configured it. That is the worst shape a bug can take —
 * silent, and indistinguishable from a legitimately empty setting.
 *
 * <p>Naming follows the column values exactly. When adding a key, add the migration and the
 * constant together, and keep the two spellings identical.
 */
public final class SettingKeys {

    private SettingKeys() {}

    // ── Cargo integration ───────────────────────────────────────
    public static final String CARGO_API_ENABLED = "cargo_api_enabled";
    public static final String CARGO_API_PROVIDER = "cargo_api_provider";
    public static final String CARGO_API_AUTO_CREATE = "cargo_api_auto_create";
    public static final String CARGO_BALANCE_ALERT_THRESHOLD = "cargo_balance_alert_threshold";
    public static final String CARGO_CHECKOUT_LIVE_PRICING = "cargo_checkout_live_pricing";
    public static final String CARGO_DISPATCH_SLA_HOURS = "cargo_dispatch_sla_hours";
    public static final String CARGO_MAX_DESI_PER_PACKAGE = "cargo_max_desi_per_package";
    public static final String CARGO_PRICE_CACHE_MINUTES = "cargo_price_cache_minutes";
    public static final String CARGO_RETURN_LABEL_ENABLED = "cargo_return_label_enabled";

    // ── Kargonomi credentials and endpoints ─────────────────────
    public static final String KARGONOMI_API_BASE_URL = "kargonomi_api_base_url";
    public static final String KARGONOMI_API_TOKEN = "kargonomi_api_token";
    public static final String KARGONOMI_APP_KEY = "kargonomi_app_key";
    public static final String KARGONOMI_WAREHOUSE_ID = "kargonomi_warehouse_id";
    public static final String KARGONOMI_WEBHOOK_SECRET = "kargonomi_webhook_secret";

    // ── Sender address used on every shipment ───────────────────
    public static final String SENDER_NAME = "sender_name";
    public static final String SENDER_PHONE = "sender_phone";
    public static final String SENDER_ADDRESS = "sender_address";
    public static final String SENDER_CITY = "sender_city";
    public static final String SENDER_DISTRICT = "sender_district";
    public static final String SENDER_POSTAL_CODE = "sender_postal_code";
    /** Kargonomi gönderici için vergi/kimlik no istiyor; boşsa INVOICE_COMPANY_TAX_ID kullanılır. */
    public static final String SENDER_TAX_NUMBER = "sender_tax_number";

    // ── Shipping pricing ────────────────────────────────────────
    public static final String DEFAULT_SHIPPING_COST = "default_shipping_cost";
    public static final String FREE_SHIPPING_THRESHOLD = "free_shipping_threshold";

    // ── E-invoice ───────────────────────────────────────────────
    public static final String INVOICE_PROVIDER = "invoice_provider";
    public static final String INVOICE_AUTO_GENERATE = "invoice_auto_generate";
    public static final String INVOICE_ADMIN_DIGEST_EMAIL = "invoice_admin_digest_email";
    public static final String INVOICE_COMPANY_TAX_ID = "invoice_company_tax_id";
    public static final String LOGO_EFATURA_ENDPOINT = "logo_efatura_endpoint";
    public static final String LOGO_EFATURA_USERNAME = "logo_efatura_username";
    public static final String LOGO_EFATURA_PASSWORD = "logo_efatura_password";
    public static final String LOGO_EFATURA_TEST_MODE = "logo_efatura_test_mode";

    // ── SMS ─────────────────────────────────────────────────────
    public static final String SMS_ENABLED = "sms_enabled";
    public static final String SMS_PROVIDER = "sms_provider";
    public static final String NETGSM_USERNAME = "netgsm_username";
    public static final String NETGSM_PASSWORD = "netgsm_password";
    public static final String NETGSM_SENDER = "netgsm_sender";

    // ── Payment ─────────────────────────────────────────────────
    public static final String PAYMENT_METHOD_BANK_TRANSFER_ENABLED = "payment_method_bank_transfer_enabled";
    public static final String PAYMENT_METHOD_CREDIT_CARD_ENABLED = "payment_method_credit_card_enabled";
    public static final String PAYMENT_METHOD_DOOR_CASH_ENABLED = "payment_method_door_cash_enabled";
    public static final String BANK_TRANSFER_QR_ENABLED = "bank_transfer_qr_enabled";
    public static final String THREEDS_ALWAYS = "threeds_always";
    public static final String THREEDS_MIN_AMOUNT = "threeds_min_amount";

    // ── Storefront identity and SEO ─────────────────────────────
    public static final String APP_BASE_URL = "app_base_url";
    public static final String SITE_NAME = "site_name";
    public static final String SITE_LOGO = "site_logo";
    public static final String RECEIPT_LOGO = "receipt_logo";
    public static final String SEO_CANONICAL_DOMAIN = "seo_canonical_domain";
    public static final String SEO_ORGANIZATION_NAME = "seo_organization_name";
    public static final String SEO_META_TITLE_HOME = "seo_meta_title_home";
    public static final String SEO_DEFAULT_META_DESCRIPTION = "seo_default_meta_description";
    public static final String SEO_DEFAULT_OG_IMAGE = "seo_default_og_image";
    public static final String SEO_LOCAL_CITY = "seo_local_city";
    public static final String SEO_LOCAL_DESCRIPTION = "seo_local_description";
    public static final String SEO_LOCAL_PRIMARY_BRANDS = "seo_local_primary_brands";
    public static final String SITE_LOGO_URL = "site_logo_url";
    public static final String CONTACT_FORM_EMAIL = "contact_form_email";
    public static final String ABANDONED_CART_ENABLED = "abandoned_cart_enabled";
}
