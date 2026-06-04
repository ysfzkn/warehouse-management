package com.warehouse.assistant.store.security;

/**
 * Resolved identity for a storefront assistant request.
 *
 * @param customerId      non-null iff the caller is authenticated as a store customer
 * @param guestSessionId  guest cookie UUID — always present (the guard issues one if missing)
 * @param ipHash          daily-salted SHA-256 of the caller IP (never the raw address)
 * @param userAgent       truncated User-Agent header
 */
public record StoreAssistantActor(
        Long customerId,
        String guestSessionId,
        String ipHash,
        String userAgent
) {
    public boolean isGuest() {
        return customerId == null;
    }
}
