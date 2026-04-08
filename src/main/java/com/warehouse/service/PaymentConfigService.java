package com.warehouse.service;

import java.util.Map;

/**
 * Service for managing payment configuration (bank transfer + iyzico).
 * Reads from site_settings DB table with fallback to PaymentProperties (application.properties).
 * This allows admin to change payment settings at runtime without server restart.
 */
public interface PaymentConfigService {

    /** Bank transfer config: bankName, iban, accountHolder, deadlineHours */
    Map<String, String> getBankTransferConfig();

    /** iyzico config: apiKey (masked for reads), secretKey (masked), baseUrl, callbackUrl, sandbox, timeoutMinutes */
    Map<String, String> getIyzicoConfig();

    /** iyzico config with UNMASKED keys — only for internal gateway use */
    Map<String, String> getIyzicoConfigRaw();

    void updateBankTransferConfig(Map<String, String> config, String updatedBy);

    void updateIyzicoConfig(Map<String, String> config, String updatedBy);
}
