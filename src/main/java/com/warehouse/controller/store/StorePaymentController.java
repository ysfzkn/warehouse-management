package com.warehouse.controller.store;

import com.warehouse.dto.payment.*;
import com.warehouse.entity.PaymentGatewayConfig;
import com.warehouse.repository.PaymentGatewayConfigRepository;
import com.warehouse.service.PaymentService;
import com.warehouse.service.payment.VirtualPosGateway;
import com.warehouse.service.payment.protocol.BankPosProtocol;
import com.warehouse.service.payment.protocol.BankPosProtocolFactory;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/payment")
public class StorePaymentController {

    private static final Logger log = LoggerFactory.getLogger(StorePaymentController.class);

    private final PaymentService paymentService;
    private final JwtService jwtService;
    private final PaymentGatewayConfigRepository gatewayConfigRepo;
    private final BankPosProtocolFactory protocolFactory;
    private final com.warehouse.service.SiteSettingService siteSettingService;
    private final com.warehouse.service.PaymentConfigService paymentConfigService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    public StorePaymentController(PaymentService paymentService, JwtService jwtService,
                                   PaymentGatewayConfigRepository gatewayConfigRepo,
                                   BankPosProtocolFactory protocolFactory,
                                   com.warehouse.service.SiteSettingService siteSettingService,
                                   com.warehouse.service.PaymentConfigService paymentConfigService) {
        this.paymentService = paymentService;
        this.jwtService = jwtService;
        this.gatewayConfigRepo = gatewayConfigRepo;
        this.protocolFactory = protocolFactory;
        this.siteSettingService = siteSettingService;
        this.paymentConfigService = paymentConfigService;
    }

    @PostMapping("/initialize")
    public ResponseEntity<?> initializePayment(@RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        if (body.get("orderId") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "orderId zorunludur."));
        }
        if (body.get("paymentMethod") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ödeme yöntemi zorunludur."));
        }
        Long orderId;
        try { orderId = ((Number) body.get("orderId")).longValue(); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", "Geçersiz orderId.")); }

        String paymentMethod = (String) body.get("paymentMethod");
        int installmentCount = 1;
        try {
            if (body.containsKey("installmentCount") && body.get("installmentCount") != null) {
                installmentCount = ((Number) body.get("installmentCount")).intValue();
            }
        } catch (Exception ignored) {}
        String idempotencyKey = (String) body.get("idempotencyKey");
        String ip = request.getRemoteAddr();

        return ResponseEntity.ok(paymentService.initializePayment(orderId, paymentMethod, installmentCount, ip, idempotencyKey));
    }

    @PostMapping("/callback")
    public void handleCallback(@RequestParam Map<String, String> params,
                                HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        PaymentCallbackResult result = paymentService.handlePaymentCallback(params);

        String redirectUrl = buildFrontendRedirectUrl(request,
            "/odeme/sonuc?success=" + result.isSuccess()
            + (result.getToken() != null ? "&token=" + result.getToken() : ""));
        response.sendRedirect(redirectUrl);
    }

    /**
     * POS callback endpoint for direct bank integrations.
     * Bank redirects customer here after 3D Secure authentication.
     * Hash verification is performed BEFORE any processing.
     */
    @PostMapping("/callback/pos/{configCode}")
    public void posCallback(@PathVariable String configCode,
                            @RequestParam Map<String, String> params,
                            HttpServletRequest request,
                            HttpServletResponse response) throws Exception {
        log.info("POS callback received: configCode={}, params={}", configCode,
                params.entrySet().stream()
                        .filter(e -> !e.getKey().toLowerCase().contains("card") && !e.getKey().toLowerCase().contains("cvv"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        Optional<PaymentGatewayConfig> configOpt = gatewayConfigRepo.findByCode(configCode);
        if (configOpt.isEmpty()) {
            log.error("POS callback: Gateway config not found: {}", configCode);
            response.sendRedirect(buildFrontendRedirectUrl(request, "/odeme/sonuc?success=false&error=config_not_found"));
            return;
        }

        PaymentGatewayConfig config = configOpt.get();

        // CRITICAL: Verify hash FIRST
        try {
            BankPosProtocol protocol = protocolFactory.getProtocol(config.getGatewayProtocol());
            if (!protocol.verifyCallbackHash(config, params)) {
                log.error("SECURITY ALERT: POS callback hash verification FAILED for config={}", configCode);
                response.sendRedirect(buildFrontendRedirectUrl(request, "/odeme/sonuc?success=false&error=hash_failed"));
                return;
            }
        } catch (Exception e) {
            log.error("POS callback hash verification error: {}", e.getMessage(), e);
            response.sendRedirect(buildFrontendRedirectUrl(request, "/odeme/sonuc?success=false&error=verification_error"));
            return;
        }

        // Add configCode so VirtualPosGateway can route correctly
        Map<String, String> enrichedParams = new HashMap<>(params);
        enrichedParams.put("_configCode", configCode);

        PaymentCallbackResult result = paymentService.handlePaymentCallback(enrichedParams);

        String redirectUrl = buildFrontendRedirectUrl(request,
            "/odeme/sonuc?success=" + result.isSuccess()
            + (result.getToken() != null ? "&token=" + result.getToken() : ""));
        response.sendRedirect(redirectUrl);
    }

    /**
     * PayTR server-to-server notification callback.
     * PayTR sends payment result here (not a customer redirect).
     * MUST respond with plain text "OK" on success.
     * PayTR retries every minute if "OK" not received.
     */
    @PostMapping("/callback/paytr/{configCode}")
    public ResponseEntity<String> paytrCallback(@PathVariable String configCode,
                                                 @RequestParam Map<String, String> params) {
        log.info("PayTR notification received: configCode={}, merchant_oid={}, status={}",
                configCode, params.get("merchant_oid"), params.get("status"));

        Optional<PaymentGatewayConfig> configOpt = gatewayConfigRepo.findByCode(configCode);
        if (configOpt.isEmpty()) {
            log.error("PayTR callback: Gateway config not found: {}", configCode);
            return ResponseEntity.ok("OK"); // Still return OK to stop retries
        }

        PaymentGatewayConfig config = configOpt.get();

        // CRITICAL: Verify HMAC-SHA256 hash
        try {
            BankPosProtocol protocol = protocolFactory.getProtocol(config.getGatewayProtocol());
            if (!protocol.verifyCallbackHash(config, params)) {
                log.error("SECURITY ALERT: PayTR callback hash verification FAILED for config={}", configCode);
                return ResponseEntity.ok("OK"); // Return OK but don't process
            }
        } catch (Exception e) {
            log.error("PayTR callback hash error: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK");
        }

        // Process the payment callback
        Map<String, String> enrichedParams = new HashMap<>(params);
        enrichedParams.put("_configCode", configCode);

        try {
            paymentService.handlePaymentCallback(enrichedParams);
        } catch (Exception e) {
            log.error("PayTR callback processing error: {}", e.getMessage(), e);
        }

        // PayTR requires plain text "OK" response
        return ResponseEntity.ok("OK");
    }

    /**
     * Returns available payment methods based on active gateway configurations.
     * Used by CheckoutPage to dynamically render payment options.
     */
    @GetMapping("/methods")
    public ResponseEntity<List<Map<String, Object>>> getPaymentMethods() {
        List<Map<String, Object>> methods = new ArrayList<>();

        // Read admin toggles from site settings (default: enabled if setting missing)
        boolean ccEnabled = !"false".equals(siteSettingService.getSetting("payment_method_credit_card_enabled"));
        boolean btEnabled = !"false".equals(siteSettingService.getSetting("payment_method_bank_transfer_enabled"));
        boolean doorEnabled = !"false".equals(siteSettingService.getSetting("payment_method_door_cash_enabled"));

        // Credit card — only if enabled AND gateway configured
        if (ccEnabled) {
            List<PaymentGatewayConfig> activeGateways = gatewayConfigRepo.findByActiveTrueOrderByPriorityAsc();
            Map<String, Object> creditCard = new LinkedHashMap<>();
            creditCard.put("method", "CREDIT_CARD");
            creditCard.put("label", "Kredi / Banka Kartı");
            creditCard.put("description", !activeGateways.isEmpty() ? "3D Secure ile güvenli ödeme" : "iyzico ile güvenli ödeme");
            creditCard.put("icon", "fas fa-credit-card");
            creditCard.put("installmentSupported", true);
            creditCard.put("active", true);
            methods.add(creditCard);
        }

        // Bank transfer — only if enabled AND IBAN configured
        if (btEnabled) {
            Map<String, String> bankCfg = paymentConfigService.getBankTransferConfig();
            String iban = bankCfg.getOrDefault("iban", "");
            if (!iban.isBlank()) {
                Map<String, Object> bankTransfer = new LinkedHashMap<>();
                bankTransfer.put("method", "BANK_TRANSFER");
                bankTransfer.put("label", "Havale / EFT");
                bankTransfer.put("description", "Banka hesabımıza havale yapın");
                bankTransfer.put("icon", "fas fa-university");
                bankTransfer.put("installmentSupported", false);
                bankTransfer.put("active", true);
                methods.add(bankTransfer);
            }
        }

        // Door payment — only if enabled
        if (doorEnabled) {
            Map<String, Object> doorCash = new LinkedHashMap<>();
            doorCash.put("method", "DOOR_CASH");
            doorCash.put("label", "Kapıda Ödeme");
            doorCash.put("description", "Teslimat sırasında nakit veya kart ile ödeyin");
            doorCash.put("icon", "fas fa-door-open");
            doorCash.put("installmentSupported", false);
            doorCash.put("active", true);
            methods.add(doorCash);
        }

        return ResponseEntity.ok(methods);
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<PaymentStatusResult> getStatus(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(paymentId));
    }

    @GetMapping("/installments")
    public ResponseEntity<InstallmentQueryResult> getInstallments(
            @RequestParam String bin, @RequestParam BigDecimal price) {
        return ResponseEntity.ok(paymentService.getInstallmentOptions(bin, price));
    }

    /**
     * Query payment status by token — used by PaymentResultPage to show error details.
     */
    @GetMapping("/status-by-token")
    public ResponseEntity<Map<String, Object>> getStatusByToken(@RequestParam String token) {
        try {
            var tx = paymentService.findTransactionByToken(token);
            if (tx == null) return ResponseEntity.ok(Map.of("status", "NOT_FOUND"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", tx.getStatus().name());
            result.put("errorCode", tx.getErrorCode() != null ? tx.getErrorCode() : "");
            result.put("errorMessage", tx.getErrorMessage() != null ? tx.getErrorMessage() : "");
            result.put("amount", tx.getAmount());
            result.put("paidAmount", tx.getPaidAmount());
            result.put("provider", tx.getPaymentProvider().name());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "ERROR", "errorMessage", e.getMessage()));
        }
    }

    /**
     * Build frontend redirect URL using app.base-url property.
     * Dev: http://localhost:3000 + path
     * Prod: https://yourdomain.com + path
     */
    private String buildFrontendRedirectUrl(HttpServletRequest request, String path) {
        return appBaseUrl.replaceAll("/$", "") + path;
    }
}
