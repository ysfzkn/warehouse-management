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
 * GVP (Garanti Virtual POS) protocol implementation.
 * Supports: Garanti BBVA.
 *
 * Uses 3D Secure with XML API.
 * Card data goes to bank's page (PCI SAQ-A compliance).
 */
@Component
public class GvpProtocol implements BankPosProtocol {

    private static final Logger log = LoggerFactory.getLogger(GvpProtocol.class);

    @Override
    public String getProtocolName() { return "GVP"; }

    @Override
    public PosPaymentInitResult initialize3DPayment(PaymentGatewayConfig config, PosPaymentRequest request) {
        try {
            String terminalId = config.getTerminalId();
            String merchantId = config.getMerchantId();
            String provisionPassword = config.getProvisionPassword();
            String storeKey = config.getStoreKey(); // GVP uses storeKey for 3D hash
            String threeDUrl = config.getThreeDUrl();

            String orderId = request.getOrderNumber();
            String amount = formatAmount(request.getAmount());
            String installment = request.getInstallmentCount() > 1 ? String.valueOf(request.getInstallmentCount()) : "";
            String okUrl = request.getOkUrl();
            String failUrl = request.getFailUrl();

            // Security hash for GVP 3D enrollment
            String terminalIdPadded = String.format("%09d", Integer.parseInt(terminalId));
            String securityData = sha512Hex(provisionPassword + terminalIdPadded);
            String hashData = sha512Hex(terminalId + orderId + amount + okUrl + failUrl + "sales" + installment + storeKey + securityData);

            // Build auto-submit HTML form
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
            html.append("<form id='gvp3d' method='POST' action='").append(escapeHtml(threeDUrl)).append("'>");
            html.append(hiddenField("mode", config.isSandbox() ? "TEST" : "PROD"));
            html.append(hiddenField("apiversion", "512"));
            html.append(hiddenField("terminalprovuserid", "PROVAUT"));
            html.append(hiddenField("terminaluserid", merchantId));
            html.append(hiddenField("terminalmerchantid", merchantId));
            html.append(hiddenField("terminalid", terminalId));
            html.append(hiddenField("txntype", "sales"));
            html.append(hiddenField("txnamount", amount));
            html.append(hiddenField("txncurrencycode", mapCurrencyCode(request.getCurrency())));
            html.append(hiddenField("txninstallmentcount", installment));
            html.append(hiddenField("orderid", orderId));
            html.append(hiddenField("successurl", okUrl));
            html.append(hiddenField("errorurl", failUrl));
            html.append(hiddenField("customeripaddress", request.getCustomerIp()));
            html.append(hiddenField("secure3dhash", hashData));
            html.append(hiddenField("secure3dsecuritylevel", "3D_PAY"));
            if (request.getCustomerEmail() != null) {
                html.append(hiddenField("customeremailaddress", request.getCustomerEmail()));
            }
            html.append("</form>");
            html.append("<script>document.getElementById('gvp3d').submit();</script>");
            html.append("</body></html>");

            log.info("GVP 3D payment initialized: orderId={}, merchantId={}, amount={}", orderId, merchantId, amount);

            return PosPaymentInitResult.builder()
                    .success(true)
                    .htmlContent(html.toString())
                    .transactionId(orderId)
                    .rawResponse(Map.of("orderId", orderId, "merchantId", merchantId))
                    .build();
        } catch (Exception e) {
            log.error("GVP initialization failed: {}", e.getMessage(), e);
            return PosPaymentInitResult.builder()
                    .success(false)
                    .errorMessage("GVP odeme baslatma hatasi: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean verifyCallbackHash(PaymentGatewayConfig config, Map<String, String> params) {
        try {
            String receivedHash = params.get("secure3dhash");
            if (receivedHash == null || receivedHash.isEmpty()) {
                log.warn("GVP callback missing secure3dhash");
                return false;
            }

            String terminalId = config.getTerminalId();
            String storeKey = config.getStoreKey();
            String orderId = params.getOrDefault("orderid", "");
            String response = params.getOrDefault("response", "");
            String mdStatus = params.getOrDefault("mdstatus", "0");

            String hashInput = orderId + terminalId + response + mdStatus + storeKey;
            String calculatedHash = sha512Hex(hashInput);

            // Timing-safe karşılaştırma. GVP hex çıktı verir; ikisini de aynı case'e
            // çevirip MessageDigest.isEqual ile karşılaştır.
            boolean valid = receivedHash != null && calculatedHash != null
                    && java.security.MessageDigest.isEqual(
                            calculatedHash.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            receivedHash.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!valid) {
                log.error("GVP HASH VERIFICATION FAILED! orderId={}", orderId);
            }
            return valid;
        } catch (Exception e) {
            log.error("GVP hash verification error: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public PosCallbackResult handle3DCallback(PaymentGatewayConfig config, Map<String, String> params) {
        String response = params.getOrDefault("response", "");
        String mdStatus = params.getOrDefault("mdstatus", "0");
        String transId = params.getOrDefault("transid", "");
        String orderId = params.getOrDefault("orderid", "");
        String errMsg = params.getOrDefault("errmsg", "");

        boolean success = "Approved".equalsIgnoreCase(response) && !"0".equals(mdStatus);

        log.info("GVP callback: orderId={}, response={}, mdStatus={}, transId={}", orderId, response, mdStatus, transId);

        Map<String, Object> sanitizedParams = new LinkedHashMap<>();
        for (var entry : params.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains("cardnumber") && !key.contains("cvv") && !key.contains("pan") && !key.contains("expiry")) {
                sanitizedParams.put(entry.getKey(), entry.getValue());
            }
        }

        return PosCallbackResult.builder()
                .success(success)
                .transactionId(transId)
                .orderId(orderId)
                .threeDSecure(!"0".equals(mdStatus))
                .mdStatus(mdStatus)
                .errorCode(!success ? params.get("procreturncode") : null)
                .errorMessage(!success ? errMsg : null)
                .rawResponse(sanitizedParams)
                .build();
    }

    @Override
    public PosRefundResult refund(PaymentGatewayConfig config, PosRefundRequest request) {
        log.info("GVP refund requested: transId={}, amount={}", request.getTransactionId(), request.getAmount());
        return PosRefundResult.builder()
                .success(false)
                .errorMessage("GVP iade islemi henuz entegre edilmedi. Garanti POS panelinden manuel iade yapiniz.")
                .build();
    }

    @Override
    public boolean testConnection(PaymentGatewayConfig config) {
        return config.getMerchantId() != null && !config.getMerchantId().isEmpty()
                && config.getTerminalId() != null && !config.getTerminalId().isEmpty()
                && config.getProvisionPassword() != null && !config.getProvisionPassword().isEmpty()
                && config.getThreeDUrl() != null && !config.getThreeDUrl().isEmpty();
    }

    // --- Utility ---

    private String sha512Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-512 hash failed", e);
        }
    }

    private String formatAmount(BigDecimal amount) {
        // GVP expects amount in kuruş (cents): 100.50 -> 10050
        return amount.multiply(new BigDecimal("100")).toBigInteger().toString();
    }

    private String mapCurrencyCode(String currency) {
        if (currency == null) return "949";
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
}
