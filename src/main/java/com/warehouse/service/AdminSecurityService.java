package com.warehouse.service;

public interface AdminSecurityService {

    /**
     * Ensures that when the current user is ADMIN a valid security code is provided.
     * Non-admin users pass through without validation.
     *
     * @param securityCode raw code provided by client (may be null/blank)
     */
    void requireSecurityCodeForAdmin(String securityCode);

    /**
     * Updates the admin security code after validating the current code.
     */
    void changeSecurityCode(String currentCode, String newCode, String confirmNewCode);

    /**
     * Returns whether a security code has been configured (either from ENV or DB).
     */
    boolean isSecurityCodeConfigured();
}

