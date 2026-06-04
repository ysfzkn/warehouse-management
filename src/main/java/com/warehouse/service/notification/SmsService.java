package com.warehouse.service.notification;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-level service for sending SMS.
 * Finds the active SmsProvider and sends the message.
 */
@Service
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    private final List<SmsProvider> providers;
    private final SiteSettingService settingService;

    public SmsService(List<SmsProvider> providers, SiteSettingService settingService) {
        this.providers = providers;
        this.settingService = settingService;
    }

    /**
     * Sends an SMS using the active provider.
     * If there are multiple active providers, uses the first one.
     */
    @Async
    public void send(String phoneNumber, String message) {
        // Is SMS enabled globally?
        if (!isSmsEnabled()) {
            logger.debug("SMS devre dışı, mesaj gönderilmiyor: phone={}", phoneNumber);
            return;
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            logger.warn("Boş telefon numarası, SMS gönderilmiyor");
            return;
        }

        SmsProvider provider = providers.stream()
                .filter(SmsProvider::isEnabled)
                .findFirst()
                .orElse(null);

        if (provider == null) {
            logger.warn("Aktif SMS sağlayıcısı yok, mesaj gönderilmiyor: phone={}", phoneNumber);
            return;
        }

        try {
            SmsSendResult result = provider.send(phoneNumber, message);
            if (!result.isSuccess()) {
                logger.warn("SMS gönderim başarısız: provider={}, phone={}, error={}",
                        result.getProviderName(), phoneNumber, result.getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("SMS gönderim exception: phone={}", phoneNumber, e);
        }
    }

    /**
     * Checks the global SMS feature flag.
     */
    public boolean isSmsEnabled() {
        String enabled = settingService.getSetting("sms_enabled");
        return "true".equalsIgnoreCase(enabled);
    }
}
