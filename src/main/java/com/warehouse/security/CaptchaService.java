package com.warehouse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * hCaptcha / reCaptcha doğrulama servisi.
 *
 * <p>Saldırgan bot trafiğine karşı register/forgot-password/contact gibi public
 * endpoint'lerde isteğe bağlı bir koruma katmanı. Site key & secret admin
 * panelinden (site_settings) veya env değişkenlerinden alınır. Boşsa servis
 * "always-pass" döner — yani CAPTCHA opsiyonel feature gate'tir.</p>
 *
 * <p>Front-end form'a hCaptcha widget'ı eklenir, kullanıcı doğrularken üretilen
 * token'ı isteğin {@code captchaToken} alanında gönderir. Controller burada
 * {@link #verify(String, String)} çağırır; false dönerse 400 ile reddeder.</p>
 *
 * <p>Provider seçimi: {@code captcha.provider} = "hcaptcha" (varsayılan)
 * veya "recaptcha"; secret env'den okunur.</p>
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final String HCAPTCHA_VERIFY_URL = "https://hcaptcha.com/siteverify";
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${captcha.provider:hcaptcha}")
    private String provider;

    @Value("${captcha.secret:}")
    private String secret;

    @Value("${captcha.enabled:false}")
    private boolean enabled;

    /**
     * Token'ı sağlayıcıya doğrulat. Service disabled veya secret boşsa true döner
     * (gating yok). Network hatasında true döner — saldırı vektörü olmasın diye
     * fail-open (alternatif fail-close üretim incident'larına yol açar).
     */
    public boolean verify(String token, String clientIp) {
        if (!enabled || secret == null || secret.isBlank()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            log.warn("[CAPTCHA] Boş token reddedildi (ip={})", clientIp);
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secret);
            body.add("response", token);
            if (clientIp != null) body.add("remoteip", clientIp);

            String url = "recaptcha".equalsIgnoreCase(provider)
                    ? RECAPTCHA_VERIFY_URL : HCAPTCHA_VERIFY_URL;

            Map response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            if (!success) {
                log.warn("[CAPTCHA] Doğrulama başarısız (provider={}, ip={}, response={})",
                        provider, clientIp, response);
            }
            return success;
        } catch (Exception e) {
            log.error("[CAPTCHA] Doğrulama sırasında hata; fail-open (ip={}): {}", clientIp, e.getMessage());
            return true; // fail-open
        }
    }
}
