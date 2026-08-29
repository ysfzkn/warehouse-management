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
 * hCaptcha / reCaptcha verification service.
 *
 * <p>An optional protection layer on public endpoints such as
 * register/forgot-password/contact against malicious bot traffic. The site key &amp;
 * secret are taken from the admin panel (site_settings) or from env variables. If empty,
 * the service returns "always-pass" — i.e. CAPTCHA is an optional feature gate.</p>
 *
 * <p>The hCaptcha widget is added to the front-end form; on verification, the user sends
 * the generated token in the request's {@code captchaToken} field. The controller calls
 * {@link #verify(String, String)} here; if it returns false, the request is rejected with 400.</p>
 *
 * <p>Provider selection: {@code captcha.provider} = "hcaptcha" (default)
 * or "recaptcha"; the secret is read from env.</p>
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

    /** Behaviour when the CAPTCHA provider cannot be reached. Defaults to fail-closed. */
    @Value("${captcha.fail-open:false}")
    private boolean failOpen;

    /**
     * Verifies the token with the provider.
     *
     * <p>Returns true when CAPTCHA is switched off or unconfigured — it is a feature
     * gate, not a hard dependency.</p>
     *
     * <p>On a provider outage the behaviour is controlled by {@code captcha.fail-open}.
     * It used to be unconditionally fail-open, which handed an attacker a trivial bypass:
     * make the verification call fail (or simply flood it until it times out) and every
     * subsequent registration and password-reset request sails through unchecked. The
     * default is now fail-closed, so an outage degrades into "please try again" rather
     * than into "no CAPTCHA at all" — set {@code CAPTCHA_FAIL_OPEN=true} to trade that
     * back for availability.</p>
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
            log.error("[CAPTCHA] Doğrulama sırasında hata (ip={}, failOpen={}): {}",
                    clientIp, failOpen, e.getMessage());
            return failOpen;
        }
    }
}
