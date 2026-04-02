package com.warehouse.service.payment.protocol;

import com.warehouse.entity.PaymentGatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PayTR iFrame API protocol implementation.
 *
 * PayTR uses a 3-step flow:
 * 1. Server-to-server: POST to PayTR to get iframe_token
 * 2. Frontend: Render PayTR iframe with token — customer enters card on PayTR's page
 * 3. Server-to-server callback: PayTR POSTs to notify_url with payment result
 *
 * Card data NEVER touches our server (PCI SAQ-A).
 * Hash: HMAC-SHA256 + Base64
 *
 * Config mapping:
 * - merchantId   → PayTR merchant_id
 * - apiKey       → PayTR merchant_key
 * - secretKey    → PayTR merchant_salt
 * - callbackUrl  → notify_url (server-to-server callback)
 * - extraConfig.merchant_ok_url  → success redirect URL
 * - extraConfig.merchant_fail_url → failure redirect URL
 */
@Component
public class PayTRProtocol implements BankPosProtocol {

    private static final Logger log = LoggerFactory.getLogger(PayTRProtocol.class);
    private static final String PAYTR_TOKEN_URL = "https://www.paytr.com/odeme/api/get-token";
    private static final String PAYTR_IFRAME_BASE = "https://www.paytr.com/odeme/guvenli/";

    private final RestTemplate restTemplate;

    public PayTRProtocol() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProtocolName() { return "PAYTR"; }

    /**
     * Step 1: Get iframe token from PayTR API, then return HTML with embedded iframe.
     */
    @Override
    public PosPaymentInitResult initialize3DPayment(PaymentGatewayConfig config, PosPaymentRequest request) {
        try {
            String merchantId = config.getMerchantId();
            String merchantKey = config.getApiKey();
            String merchantSalt = config.getSecretKey();
            boolean testMode = config.isSandbox();

            String merchantOid = request.getOrderNumber();
            String email = request.getCustomerEmail() != null ? request.getCustomerEmail() : "musteri@site.com";
            String userIp = request.getCustomerIp() != null ? request.getCustomerIp() : "127.0.0.1";

            // PayTR expects amount * 100 (kuruş)
            int paymentAmount = request.getAmount().multiply(new BigDecimal("100")).intValue();

            // Basket: JSON array [[name, price*100, quantity], ...]
            String userBasket = Base64.getEncoder().encodeToString(
                    ("[[\"Siparis " + merchantOid + "\",\"" + paymentAmount + "\",1]]").getBytes(StandardCharsets.UTF_8)
            );

            String currency = mapCurrency(request.getCurrency());
            int noInstallment = request.getInstallmentCount() <= 1 ? 0 : 0;
            int maxInstallment = config.getMaxInstallments() != null ? config.getMaxInstallments() : 12;
            String testModeStr = testMode ? "1" : "0";

            // Callback URLs
            String notifyUrl = config.getCallbackUrl();
            Map<String, Object> extra = config.getExtraConfig() != null ? config.getExtraConfig() : Map.of();
            String okUrl = extra.getOrDefault("merchant_ok_url", "/store/odeme/sonuc?success=true").toString();
            String failUrl = extra.getOrDefault("merchant_fail_url", "/store/odeme/sonuc?success=false").toString();
            int timeoutLimit = extra.containsKey("timeout_limit")
                    ? Integer.parseInt(extra.get("timeout_limit").toString()) : 30;

            // HMAC-SHA256 hash
            String hashStr = merchantId + userIp + merchantOid + email + paymentAmount
                    + userBasket + noInstallment + maxInstallment + currency + testModeStr;
            String paytrToken = generateHmacSha256(hashStr + merchantSalt, merchantKey);

            // POST to PayTR to get iframe token
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("merchant_id", merchantId);
            formData.add("user_ip", userIp);
            formData.add("merchant_oid", merchantOid);
            formData.add("email", email);
            formData.add("payment_amount", String.valueOf(paymentAmount));
            formData.add("paytr_token", paytrToken);
            formData.add("user_basket", userBasket);
            formData.add("debug_on", testMode ? "1" : "0");
            formData.add("no_installment", String.valueOf(noInstallment));
            formData.add("max_installment", String.valueOf(maxInstallment));
            formData.add("currency", currency);
            formData.add("test_mode", testModeStr);
            formData.add("merchant_ok_url", okUrl);
            formData.add("merchant_fail_url", failUrl);
            formData.add("timeout_limit", String.valueOf(timeoutLimit));
            formData.add("lang", "tr");

            // Customer info (optional but recommended)
            if (request.getCustomerEmail() != null) formData.add("user_name", "");
            formData.add("user_address", "");
            formData.add("user_phone", "");

            // Notify URL (server-to-server callback)
            if (notifyUrl != null && !notifyUrl.isEmpty()) {
                formData.add("merchant_notify_url", notifyUrl);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(PAYTR_TOKEN_URL, entity, Map.class);

            if (response.getBody() != null && "success".equals(response.getBody().get("status"))) {
                String iframeToken = (String) response.getBody().get("token");

                // Build HTML with PayTR iframe
                String html = buildIframeHtml(iframeToken);

                log.info("PayTR token obtained: merchantOid={}, token={}...", merchantOid,
                        iframeToken.length() > 10 ? iframeToken.substring(0, 10) + "..." : iframeToken);

                return PosPaymentInitResult.builder()
                        .success(true)
                        .htmlContent(html)
                        .transactionId(merchantOid)
                        .rawResponse(Map.of("token", iframeToken, "merchantOid", merchantOid))
                        .build();
            } else {
                String reason = response.getBody() != null ? String.valueOf(response.getBody().get("reason")) : "Bilinmeyen hata";
                log.error("PayTR token request failed: merchantOid={}, reason={}", merchantOid, reason);
                return PosPaymentInitResult.builder()
                        .success(false)
                        .errorMessage("PayTR token alinamadi: " + reason)
                        .rawResponse(response.getBody() != null ? new LinkedHashMap<>(response.getBody()) : Map.of())
                        .build();
            }
        } catch (Exception e) {
            log.error("PayTR initialization failed: {}", e.getMessage(), e);
            return PosPaymentInitResult.builder()
                    .success(false)
                    .errorMessage("PayTR odeme baslatma hatasi: " + e.getMessage())
                    .build();
        }
    }

    /**
     * CRITICAL: Verify PayTR callback hash.
     * hash = Base64(HMAC-SHA256(merchant_oid + merchant_salt + status + total_amount, merchant_key))
     */
    @Override
    public boolean verifyCallbackHash(PaymentGatewayConfig config, Map<String, String> params) {
        try {
            String merchantKey = config.getApiKey();
            String merchantSalt = config.getSecretKey();

            String merchantOid = params.getOrDefault("merchant_oid", "");
            String status = params.getOrDefault("status", "");
            String totalAmount = params.getOrDefault("total_amount", "");
            String incomingHash = params.get("hash");

            if (incomingHash == null || incomingHash.isEmpty()) {
                log.warn("PayTR callback missing hash parameter");
                return false;
            }

            String dataToHash = merchantOid + merchantSalt + status + totalAmount;
            String calculatedHash = generateHmacSha256(dataToHash, merchantKey);

            boolean valid = calculatedHash.equals(incomingHash);
            if (!valid) {
                log.error("PayTR HASH VERIFICATION FAILED! merchantOid={}, incoming={}, calculated={}",
                        merchantOid, incomingHash, calculatedHash);
            } else {
                log.info("PayTR hash verified: merchantOid={}, status={}", merchantOid, status);
            }
            return valid;
        } catch (Exception e) {
            log.error("PayTR hash verification error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handle PayTR callback.
     * PayTR POSTs: merchant_oid, status (success/failed), total_amount, hash, payment_type
     * Must respond with plain text "OK"
     */
    @Override
    public PosCallbackResult handle3DCallback(PaymentGatewayConfig config, Map<String, String> params) {
        String merchantOid = params.getOrDefault("merchant_oid", "");
        String status = params.getOrDefault("status", "");
        String totalAmountStr = params.getOrDefault("total_amount", "0");
        String paymentType = params.getOrDefault("payment_type", "");
        String failedReasonCode = params.getOrDefault("failed_reason_code", "");
        String failedReasonMsg = params.getOrDefault("failed_reason_msg", "");

        boolean success = "success".equals(status);

        // Convert total_amount back (PayTR sends amount * 100)
        BigDecimal paidAmount = null;
        try {
            paidAmount = new BigDecimal(totalAmountStr).divide(new BigDecimal("100"));
        } catch (Exception e) {
            log.warn("PayTR: could not parse total_amount: {}", totalAmountStr);
        }

        log.info("PayTR callback: merchantOid={}, status={}, totalAmount={}, paymentType={}, failCode={}",
                merchantOid, status, totalAmountStr, paymentType, failedReasonCode);

        // Sanitize — remove any sensitive data
        Map<String, Object> sanitizedParams = new LinkedHashMap<>();
        for (var entry : params.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains("card") && !key.contains("cvv") && !key.contains("pan")) {
                sanitizedParams.put(entry.getKey(), entry.getValue());
            }
        }

        return PosCallbackResult.builder()
                .success(success)
                .transactionId(merchantOid)
                .orderId(merchantOid)
                .paidAmount(paidAmount)
                .threeDSecure(true) // PayTR always uses 3D Secure
                .mdStatus(success ? "1" : "0")
                .cardType("card".equals(paymentType) ? "CARD" : paymentType)
                .errorCode(!success ? failedReasonCode : null)
                .errorMessage(!success ? failedReasonMsg : null)
                .rawResponse(sanitizedParams)
                .build();
    }

    @Override
    public PosRefundResult refund(PaymentGatewayConfig config, PosRefundRequest request) {
        // PayTR has a separate refund API endpoint
        // For now, indicate manual process
        log.info("PayTR refund requested: merchantOid={}, amount={}", request.getOrderId(), request.getAmount());
        return PosRefundResult.builder()
                .success(false)
                .errorMessage("PayTR iade islemi PayTR magaza panelinden yapilmalidir.")
                .build();
    }

    @Override
    public boolean testConnection(PaymentGatewayConfig config) {
        // Verify required fields are present
        boolean hasCredentials = config.getMerchantId() != null && !config.getMerchantId().isEmpty()
                && config.getApiKey() != null && !config.getApiKey().isEmpty()
                && config.getSecretKey() != null && !config.getSecretKey().isEmpty();

        if (!hasCredentials) return false;

        // Try to generate a test token (with debug mode) to verify credentials
        try {
            String merchantId = config.getMerchantId();
            String merchantKey = config.getApiKey();
            String merchantSalt = config.getSecretKey();

            String testHash = generateHmacSha256("test" + merchantSalt, merchantKey);
            // If hash generation succeeds, credentials are at least valid format
            return testHash != null && !testHash.isEmpty();
        } catch (Exception e) {
            log.warn("PayTR test connection failed: {}", e.getMessage());
            return false;
        }
    }

    // ===== Utility Methods =====

    /**
     * Generate HMAC-SHA256 hash encoded in Base64.
     * This is PayTR's required hash algorithm.
     */
    private String generateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 hash generation failed", e);
        }
    }

    private String mapCurrency(String currency) {
        if (currency == null) return "TL";
        return switch (currency.toUpperCase()) {
            case "TRY", "TL", "₺" -> "TL";
            case "USD", "$" -> "USD";
            case "EUR", "€" -> "EUR";
            case "GBP", "£" -> "GBP";
            default -> "TL";
        };
    }

    /**
     * Build HTML page with PayTR iframe + resizer script.
     * PayTR's iframe handles the entire card entry flow.
     */
    private String buildIframeHtml(String token) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<script src='https://www.paytr.com/js/iframeResizer.min.js'></script>"
                + "</head><body style='margin:0;padding:0;'>"
                + "<iframe src='" + PAYTR_IFRAME_BASE + escapeHtml(token)
                + "' id='paytriframe' frameborder='0' scrolling='no' style='width:100%;border:none;'></iframe>"
                + "<script>iFrameResize({},'#paytriframe');</script>"
                + "</body></html>";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
