package com.warehouse.service.payment.protocol;

import com.warehouse.entity.PaymentGatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * NestPay (Asseco EST) protocol implementation.
 * Supports: İş Bankası, Akbank, Halk Bank, TEB, DenizBank, ING, Anadolubank, Ziraat, Kuveyt Türk.
 *
 * Uses 3D Pay Hosting model — card data goes directly to the bank's page.
 * Our server NEVER sees card numbers (PCI SAQ-A compliance).
 */
@Component
public class NestPayProtocol implements BankPosProtocol {

    private static final Logger log = LoggerFactory.getLogger(NestPayProtocol.class);

    @Override
    public String getProtocolName() { return "NESTPAY"; }

    /**
     * Generate HTML form for 3D Secure redirect to bank.
     * The form auto-submits, sending the customer to the bank's card entry page.
     */
    @Override
    public PosPaymentInitResult initialize3DPayment(PaymentGatewayConfig config, PosPaymentRequest request) {
        try {
            String clientId = config.getMerchantId();
            String storeKey = config.getStoreKey();
            String threeDUrl = config.getThreeDUrl();

            String oid = request.getOrderNumber();
            String amount = request.getAmount().toPlainString();
            String currency = mapCurrencyCode(request.getCurrency());
            String tranType = "Auth";
            String instalment = request.getInstallmentCount() > 1 ? String.valueOf(request.getInstallmentCount()) : "";
            String rnd = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String okUrl = request.getOkUrl();
            String failUrl = request.getFailUrl();
            String storeType = "3d_pay_hosting";

            // Hash = Base64(SHA-512(clientId|oid|amount|okUrl|failUrl|TranType|Instalment|rnd|storeKey))
            String hashStr = clientId + "|" + oid + "|" + amount + "|" + okUrl + "|" + failUrl
                    + "|" + tranType + "|" + instalment + "|" + rnd + "|" + storeKey;
            String hash = generateHash(hashStr);

            // Build auto-submit HTML form
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
            html.append("<form id='nestpay3d' method='POST' action='").append(escapeHtml(threeDUrl)).append("'>");
            html.append(hiddenField("clientid", clientId));
            html.append(hiddenField("amount", amount));
            html.append(hiddenField("oid", oid));
            html.append(hiddenField("okUrl", okUrl));
            html.append(hiddenField("failUrl", failUrl));
            html.append(hiddenField("TranType", tranType));
            html.append(hiddenField("Instalment", instalment));
            html.append(hiddenField("rnd", rnd));
            html.append(hiddenField("hash", hash));
            html.append(hiddenField("currency", currency));
            html.append(hiddenField("storetype", storeType));
            html.append(hiddenField("lang", "tr"));
            html.append(hiddenField("hashAlgorithm", "ver3"));
            if (request.getCustomerEmail() != null) {
                html.append(hiddenField("email", request.getCustomerEmail()));
            }
            html.append("</form>");
            html.append("<script>document.getElementById('nestpay3d').submit();</script>");
            html.append("</body></html>");

            log.info("NestPay 3D payment initialized: oid={}, clientId={}, amount={}", oid, clientId, amount);

            return PosPaymentInitResult.builder()
                    .success(true)
                    .htmlContent(html.toString())
                    .transactionId(oid)
                    .rawResponse(Map.of("oid", oid, "rnd", rnd, "clientId", clientId))
                    .build();
        } catch (Exception e) {
            log.error("NestPay initialization failed: {}", e.getMessage(), e);
            return PosPaymentInitResult.builder()
                    .success(false)
                    .errorMessage("NestPay odeme baslatma hatasi: " + e.getMessage())
                    .build();
        }
    }

    /**
     * CRITICAL: Verify callback hash before processing.
     * NestPay hash verification:
     * 1. Read param names from HASHPARAMS (pipe-separated)
     * 2. Concatenate their values + storeKey
     * 3. SHA-512 + Base64
     * 4. Compare to HASH field
     */
    @Override
    public boolean verifyCallbackHash(PaymentGatewayConfig config, Map<String, String> params) {
        try {
            String hashParams = params.get("HASHPARAMS");
            String hashParamsVal = params.get("HASHPARAMSVAL");
            String receivedHash = params.get("HASH");

            if (hashParams == null || receivedHash == null) {
                log.warn("NestPay callback missing HASHPARAMS or HASH");
                return false;
            }

            // Build hash value from parameter names listed in HASHPARAMS
            String[] paramNames = hashParams.split(":");
            StringBuilder hashBuilder = new StringBuilder();
            for (String paramName : paramNames) {
                String value = params.getOrDefault(paramName, "");
                hashBuilder.append(value);
            }
            hashBuilder.append(config.getStoreKey());

            String calculatedHash = generateHash(hashBuilder.toString());
            // Timing-safe comparison (so an attacker cannot guess the hash bytes one by one)
            boolean valid = receivedHash != null && calculatedHash != null
                    && java.security.MessageDigest.isEqual(
                            calculatedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            receivedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            if (!valid) {
                log.error("NestPay HASH VERIFICATION FAILED! Possible fraud attempt. " +
                        "oid={}, receivedHash={}, calculatedHash={}", params.get("oid"), receivedHash, calculatedHash);
            } else {
                log.info("NestPay hash verified successfully: oid={}", params.get("oid"));
            }

            return valid;
        } catch (Exception e) {
            log.error("NestPay hash verification error: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public PosCallbackResult handle3DCallback(PaymentGatewayConfig config, Map<String, String> params) {
        String response = params.getOrDefault("Response", "");
        String mdStatus = params.getOrDefault("mdStatus", "0");
        String transId = params.getOrDefault("TransId", "");
        String oid = params.getOrDefault("oid", "");
        String procReturnCode = params.getOrDefault("ProcReturnCode", "");
        String errMsg = params.getOrDefault("ErrMsg", "");

        // mdStatus: 1 = Full 3D auth, 2 = Card not enrolled, 3 = Bank/card error, 4 = Merchant/bank error
        boolean threeDSuccess = "1".equals(mdStatus) || "2".equals(mdStatus) || "3".equals(mdStatus) || "4".equals(mdStatus);
        boolean paymentSuccess = "Approved".equals(response) && "00".equals(procReturnCode) && threeDSuccess;

        log.info("NestPay callback: oid={}, response={}, mdStatus={}, procReturnCode={}, transId={}",
                oid, response, mdStatus, procReturnCode, transId);

        // Sanitize raw response — remove any potential card data
        Map<String, Object> sanitizedParams = new LinkedHashMap<>();
        for (var entry : params.entrySet()) {
            String key = entry.getKey().toLowerCase();
            // Never store full card number or CVV
            if (!key.contains("cardnumber") && !key.contains("cvv") && !key.contains("pan") && !key.contains("expiry")) {
                sanitizedParams.put(entry.getKey(), entry.getValue());
            }
        }

        return PosCallbackResult.builder()
                .success(paymentSuccess)
                .transactionId(transId)
                .orderId(oid)
                .threeDSecure(threeDSuccess)
                .mdStatus(mdStatus)
                .cardLastFour(maskToLastFour(params.get("MaskedPan")))
                .cardType(params.get("CardType"))
                .errorCode(!paymentSuccess ? procReturnCode : null)
                .errorMessage(!paymentSuccess ? errMsg : null)
                .rawResponse(sanitizedParams)
                .build();
    }

    @Override
    public PosRefundResult refund(PaymentGatewayConfig config, PosRefundRequest request) {
        // NestPay refund would be an XML POST to /fim/api with TranType=Credit
        // For now, return a stub indicating manual bank process needed
        log.info("NestPay refund requested: transId={}, amount={}", request.getTransactionId(), request.getAmount());
        return PosRefundResult.builder()
                .success(false)
                .errorMessage("NestPay iade islemi henuz entegre edilmedi. Banka panelinden manuel iade yapiniz.")
                .build();
    }

    @Override
    public boolean testConnection(PaymentGatewayConfig config) {
        // Basic validation: check required fields are present
        return config.getMerchantId() != null && !config.getMerchantId().isEmpty()
                && config.getStoreKey() != null && !config.getStoreKey().isEmpty()
                && config.getThreeDUrl() != null && !config.getThreeDUrl().isEmpty();
    }

    // --- Utility methods ---

    private String generateHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("SHA-512 hash generation failed", e);
        }
    }

    private String mapCurrencyCode(String currency) {
        if (currency == null) return "949"; // TRY
        return switch (currency.toUpperCase()) {
            case "TRY", "TL" -> "949";
            case "USD" -> "840";
            case "EUR" -> "978";
            case "GBP" -> "826";
            default -> "949";
        };
    }

    private String hiddenField(String name, String value) {
        return "<input type='hidden' name='" + escapeHtml(name) + "' value='" + escapeHtml(value != null ? value : "") + "' />";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String maskToLastFour(String maskedPan) {
        if (maskedPan == null || maskedPan.length() < 4) return null;
        return maskedPan.substring(maskedPan.length() - 4);
    }
}
