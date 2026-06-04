package com.warehouse.service.impl;

import com.warehouse.config.PaymentProperties;
import com.warehouse.service.PaymentConfigService;
import com.warehouse.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hybrid payment config: reads from DB (site_settings), falls back to PaymentProperties.
 * Allows runtime changes without server restart.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private final SiteSettingService settingService;
    private final PaymentProperties paymentProperties;

    private String getOrFallback(String dbKey, String fallback) {
        String val = settingService.getSetting(dbKey);
        return (val != null && !val.isEmpty()) ? val : (fallback != null ? fallback : "");
    }

    @Override
    public Map<String, String> getBankTransferConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("bankName", getOrFallback("payment_bank_name", paymentProperties.getBankTransfer().getBankName()));
        config.put("iban", getOrFallback("payment_bank_iban", paymentProperties.getBankTransfer().getIban()));
        config.put("accountHolder", getOrFallback("payment_bank_account_holder", paymentProperties.getBankTransfer().getAccountHolder()));
        config.put("deadlineHours", getOrFallback("payment_bank_deadline_hours", String.valueOf(paymentProperties.getBankTransfer().getDeadlineHours())));
        config.put("configured", (!config.get("iban").isEmpty()) ? "true" : "false");

        // FAST QR (Bank transfer via QR code) — optional, an extra option the admin enables via a toggle
        String qrEnabled = getOrFallback("bank_transfer_qr_enabled", "false");
        String qrImage = getOrFallback("bank_transfer_qr_image", "");
        // Show to the customer only if the toggle is on and an image has been uploaded
        boolean qrActive = "true".equalsIgnoreCase(qrEnabled) && !qrImage.isEmpty();
        config.put("qrEnabled", String.valueOf(qrActive));
        config.put("qrImage", qrActive ? qrImage : "");
        config.put("qrBankName", qrActive ? getOrFallback("bank_transfer_qr_bank_name", "") : "");
        config.put("qrDescription", qrActive ? getOrFallback("bank_transfer_qr_description", "") : "");
        return config;
    }

    @Override
    public Map<String, String> getIyzicoConfig() {
        Map<String, String> config = getIyzicoConfigRaw();
        // Mask sensitive keys
        String apiKey = config.get("apiKey");
        config.put("apiKey", maskKey(apiKey));
        config.put("secretKey", maskKey(config.get("secretKey")));
        return config;
    }

    @Override
    public Map<String, String> getIyzicoConfigRaw() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("apiKey", getOrFallback("payment_iyzico_api_key", paymentProperties.getIyzico().getApiKey()));
        config.put("secretKey", getOrFallback("payment_iyzico_secret_key", paymentProperties.getIyzico().getSecretKey()));
        config.put("baseUrl", getOrFallback("payment_iyzico_base_url", paymentProperties.getIyzico().getBaseUrl()));
        config.put("callbackUrl", getOrFallback("payment_iyzico_callback_url", paymentProperties.getIyzico().getCallbackUrl()));
        config.put("sandbox", getOrFallback("payment_iyzico_sandbox", String.valueOf(paymentProperties.isSandbox())));
        config.put("timeoutMinutes", getOrFallback("payment_iyzico_timeout_minutes", String.valueOf(paymentProperties.getIyzico().getTimeoutMinutes())));
        config.put("configured", (!config.get("apiKey").isEmpty()) ? "true" : "false");
        return config;
    }

    @Override
    public void updateBankTransferConfig(Map<String, String> config, String updatedBy) {
        Map<String, String> toSave = new LinkedHashMap<>();
        if (config.containsKey("bankName")) toSave.put("payment_bank_name", config.get("bankName"));
        if (config.containsKey("iban")) toSave.put("payment_bank_iban", config.get("iban"));
        if (config.containsKey("accountHolder")) toSave.put("payment_bank_account_holder", config.get("accountHolder"));
        if (config.containsKey("deadlineHours")) toSave.put("payment_bank_deadline_hours", config.get("deadlineHours"));
        // QR settings
        if (config.containsKey("qrEnabled")) toSave.put("bank_transfer_qr_enabled", config.get("qrEnabled"));
        if (config.containsKey("qrImage")) toSave.put("bank_transfer_qr_image", config.get("qrImage"));
        if (config.containsKey("qrBankName")) toSave.put("bank_transfer_qr_bank_name", config.get("qrBankName"));
        if (config.containsKey("qrDescription")) toSave.put("bank_transfer_qr_description", config.get("qrDescription"));
        settingService.updateSettings(toSave, updatedBy);
    }

    @Override
    public void updateIyzicoConfig(Map<String, String> config, String updatedBy) {
        Map<String, String> toSave = new LinkedHashMap<>();
        if (config.containsKey("apiKey")) toSave.put("payment_iyzico_api_key", config.get("apiKey"));
        if (config.containsKey("secretKey")) toSave.put("payment_iyzico_secret_key", config.get("secretKey"));
        if (config.containsKey("baseUrl")) toSave.put("payment_iyzico_base_url", config.get("baseUrl"));
        if (config.containsKey("callbackUrl")) toSave.put("payment_iyzico_callback_url", config.get("callbackUrl"));
        if (config.containsKey("sandbox")) toSave.put("payment_iyzico_sandbox", config.get("sandbox"));
        if (config.containsKey("timeoutMinutes")) toSave.put("payment_iyzico_timeout_minutes", config.get("timeoutMinutes"));
        settingService.updateSettings(toSave, updatedBy);
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.length() > 8) return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
        return "****";
    }
}
