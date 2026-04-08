package com.warehouse.assistant.core.ratelimit;

/**
 * Rate limit buckets, keyed by the actor category.
 * Limits are sourced from {@code assistant.ratelimit.*} in application.properties.
 */
public enum RateLimitScope {
    /** Guest per-session cap (cookie-scoped). After this, the UI shows a login prompt. */
    GUEST_SESSION,
    /** Guest per-IP-per-day cap. Hard stop for abusive traffic. */
    GUEST_IP,
    /** Authenticated store customer, per-hour. */
    CUSTOMER_HOURLY,
    /** Authenticated store customer, per-day. */
    CUSTOMER_DAILY,
    /** Authenticated WMS user (ADMIN / STOCK_IN / STOCK_OUT), per-hour. */
    WMS_HOURLY
}
