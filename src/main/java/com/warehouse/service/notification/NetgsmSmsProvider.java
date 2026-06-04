package com.warehouse.service.notification;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Netgsm SMS provider.
 * https://www.netgsm.com.tr/dokuman/
 *
 * Configuration (from the site_settings table):
 * - sms_provider = "NETGSM"
 * - netgsm_username (user code)
 * - netgsm_password
 * - netgsm_sender (approved header, e.g. "MAGAZAM")
 *
 * Note: Netgsm deliveries require an approved header.
 */
@Component
public class NetgsmSmsProvider implements SmsProvider {

    private static final Logger logger = LoggerFactory.getLogger(NetgsmSmsProvider.class);
    private static final String API_URL = "https://api.netgsm.com.tr/sms/send/otp";
    private static final String DEFAULT_SENDER = "BILGI";

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate;

    public NetgsmSmsProvider(SiteSettingService settingService) {
        this.settingService = settingService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "NETGSM";
    }

    @Override
    public SmsSendResult send(String phoneNumber, String message) {
        if (!isEnabled()) {
            return SmsSendResult.failure(getProviderName(), "NOT_CONFIGURED",
                    "Netgsm bilgileri yapılandırılmamış.");
        }

        String username = settingService.getSetting("netgsm_username");
        String password = settingService.getSetting("netgsm_password");
        String sender = settingService.getSetting("netgsm_sender");
        if (sender == null || sender.isBlank()) sender = DEFAULT_SENDER;

        // Normalize the phone number (strip +90 / 0 prefix)
        String normalizedPhone = normalizePhone(phoneNumber);
        if (normalizedPhone == null) {
            return SmsSendResult.failure(getProviderName(), "INVALID_PHONE",
                    "Geçersiz telefon numarası: " + phoneNumber);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("usercode", username);
            form.add("password", password);
            form.add("gsmno", normalizedPhone);
            form.add("message", message);
            form.add("msgheader", sender);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);
            String body = response.getBody();

            // Netgsm API response: "00 BULKID" = success, "20", "30", "40" etc. = error
            if (body != null && body.startsWith("00")) {
                String bulkId = body.length() > 3 ? body.substring(3).trim() : "";
                logger.info("Netgsm SMS gönderildi: phone={}, bulkId={}", normalizedPhone, bulkId);
                return SmsSendResult.success(getProviderName(), bulkId);
            } else {
                String errorCode = body != null ? body.split(" ")[0] : "UNKNOWN";
                String errorMessage = interpretNetgsmError(errorCode);
                logger.warn("Netgsm SMS hatası: phone={}, code={}, message={}",
                        normalizedPhone, errorCode, errorMessage);
                return SmsSendResult.failure(getProviderName(), errorCode, errorMessage);
            }
        } catch (Exception e) {
            logger.error("Netgsm SMS gönderim exception: phone={}, error={}",
                    normalizedPhone, e.getMessage(), e);
            return SmsSendResult.failure(getProviderName(), "EXCEPTION", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        String provider = settingService.getSetting("sms_provider");
        if (!"NETGSM".equalsIgnoreCase(provider)) return false;
        String username = settingService.getSetting("netgsm_username");
        String password = settingService.getSetting("netgsm_password");
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    /** "+905551234567" or "05551234567" -> "5551234567" (10 digits) */
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("90")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() != 10) return null;
        if (!digits.startsWith("5")) return null; // a GSM number must start with 5
        return digits;
    }

    private String interpretNetgsmError(String code) {
        return switch (code) {
            case "20" -> "Mesaj metni hatalı veya çok uzun";
            case "30" -> "Geçersiz kullanıcı adı / şifre";
            case "40" -> "Mesaj başlığı (header) onaylı değil";
            case "50" -> "Gönderim zamanı geçersiz";
            case "60" -> "Hesapta yeterli kredi yok";
            case "70" -> "Parametre eksik";
            case "85" -> "Mükerrer gönderim";
            case "100" -> "Sistem hatası";
            default -> "Bilinmeyen Netgsm hatası (kod: " + code + ")";
        };
    }
}
