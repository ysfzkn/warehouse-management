package com.warehouse.service.notification;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock SMS sağlayıcısı - geliştirme ve test için.
 * Gerçek SMS göndermez, log'a yazar.
 */
@Component
public class MockSmsProvider implements SmsProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockSmsProvider.class);

    private final SiteSettingService settingService;

    public MockSmsProvider(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public SmsSendResult send(String phoneNumber, String message) {
        String messageId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        logger.info("[MOCK SMS] to={}, messageId={}, message=\"{}\"",
                phoneNumber, messageId, message);
        return SmsSendResult.success(getProviderName(), messageId);
    }

    @Override
    public boolean isEnabled() {
        // Mock provider sadece MOCK mode'da aktif
        String provider = settingService.getSetting("sms_provider");
        return provider == null || provider.isBlank() || "MOCK".equalsIgnoreCase(provider);
    }
}
