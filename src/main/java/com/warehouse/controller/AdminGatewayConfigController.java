package com.warehouse.controller;

import com.warehouse.entity.PaymentGatewayConfig;
import com.warehouse.repository.PaymentGatewayConfigRepository;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.payment.protocol.BankPosProtocol;
import com.warehouse.service.payment.protocol.BankPosProtocolFactory;
import com.warehouse.util.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/payment-gateways")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGatewayConfigController {

    private final PaymentGatewayConfigRepository configRepo;
    private final AdminSecurityService adminSecurityService;
    private final BankPosProtocolFactory protocolFactory;

    public AdminGatewayConfigController(PaymentGatewayConfigRepository configRepo,
                                         AdminSecurityService adminSecurityService,
                                         BankPosProtocolFactory protocolFactory) {
        this.configRepo = configRepo;
        this.adminSecurityService = adminSecurityService;
        this.protocolFactory = protocolFactory;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(configRepo.findAll().stream()
                .map(this::toMaskedDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return configRepo.findById(id)
                .map(c -> ResponseEntity.ok(toMaskedDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody PaymentGatewayConfig config,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        config.setId(null);
        PaymentGatewayConfig saved = configRepo.save(config);
        return ResponseEntity.ok(toMaskedDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody PaymentGatewayConfig update,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(existing -> {
            if (hasValue(update.getDisplayName())) existing.setDisplayName(update.getDisplayName());
            if (hasValue(update.getGatewayProtocol())) existing.setGatewayProtocol(update.getGatewayProtocol());
            if (update.getBankCode() != null) existing.setBankCode(update.getBankCode());
            if (hasValue(update.getMerchantId())) existing.setMerchantId(update.getMerchantId());
            if (hasValue(update.getTerminalId())) existing.setTerminalId(update.getTerminalId());
            // Credentials: only overwrite if non-empty (prevents blanking existing secrets)
            if (hasValue(update.getStoreKey())) existing.setStoreKey(update.getStoreKey());
            if (hasValue(update.getProvisionPassword())) existing.setProvisionPassword(update.getProvisionPassword());
            if (hasValue(update.getApiKey())) existing.setApiKey(update.getApiKey());
            if (hasValue(update.getSecretKey())) existing.setSecretKey(update.getSecretKey());
            if (update.getBaseUrl() != null) existing.setBaseUrl(update.getBaseUrl());
            if (update.getThreeDUrl() != null) existing.setThreeDUrl(update.getThreeDUrl());
            if (update.getCallbackUrl() != null) existing.setCallbackUrl(update.getCallbackUrl());
            existing.setSandbox(update.isSandbox());
            existing.setPriority(update.getPriority());
            if (update.getSupportedCards() != null) existing.setSupportedCards(update.getSupportedCards());
            if (update.getMaxInstallments() != null) existing.setMaxInstallments(update.getMaxInstallments());
            if (update.getExtraConfig() != null) existing.setExtraConfig(update.getExtraConfig());
            return ResponseEntity.ok(toMaskedDto(configRepo.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Map<String, String>> activate(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(c -> {
            c.setActive(true);
            configRepo.save(c);
            return ResponseEntity.ok(Map.of("message", c.getDisplayName() + " aktiflestirildi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivate(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(c -> {
            c.setActive(false);
            configRepo.save(c);
            return ResponseEntity.ok(Map.of("message", c.getDisplayName() + " deaktif edildi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/set-default")
    public ResponseEntity<Map<String, String>> setDefault(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(c -> {
            // Remove default from all others
            configRepo.findAll().forEach(other -> {
                if (other.isDefaultGateway() && !other.getId().equals(id)) {
                    other.setDefaultGateway(false);
                    configRepo.save(other);
                }
            });
            c.setDefaultGateway(true);
            c.setActive(true);
            configRepo.save(c);
            return ResponseEntity.ok(Map.of("message", c.getDisplayName() + " varsayilan gateway olarak ayarlandi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(config -> {
            try {
                String protocol = config.getGatewayProtocol();

                // iyzico has its own test logic (not a BankPosProtocol)
                if ("IYZICO".equals(protocol)) {
                    return testIyzicoConnection(config);
                }

                BankPosProtocol posProtocol = protocolFactory.getProtocol(protocol);
                boolean success = posProtocol.testConnection(config);
                return ResponseEntity.ok(Map.<String, Object>of(
                        "success", success,
                        "message", success ? "Bağlantı başarılı." : "Bağlantı başarısız. Yapılandırmayı kontrol edin."
                ));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.<String, Object>of("success", false, "message", "Hata: " + e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Test iyzico connectivity by attempting a BIN query (lightweight, no payment) */
    private ResponseEntity<Map<String, Object>> testIyzicoConnection(PaymentGatewayConfig config) {
        try {
            com.iyzipay.Options opts = new com.iyzipay.Options();
            opts.setApiKey(config.getApiKey());
            opts.setSecretKey(config.getSecretKey());
            opts.setBaseUrl(config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()
                ? config.getBaseUrl()
                : (config.isSandbox() ? "https://sandbox-api.iyzipay.com" : "https://api.iyzipay.com"));

            // Use installment info query as a health check (lightweight API call)
            com.iyzipay.request.RetrieveInstallmentInfoRequest req = new com.iyzipay.request.RetrieveInstallmentInfoRequest();
            req.setLocale(com.iyzipay.model.Locale.TR.getValue());
            req.setBinNumber("552879"); // iyzico sandbox test BIN
            req.setPrice(new java.math.BigDecimal("100"));

            com.iyzipay.model.InstallmentInfo result = com.iyzipay.model.InstallmentInfo.retrieve(req, opts);

            boolean success = "success".equals(result.getStatus());
            String msg = success
                ? "iyzico bağlantısı başarılı. API key ve secret key geçerli."
                : "iyzico bağlantı hatası: " + result.getErrorMessage();

            return ResponseEntity.ok(Map.<String, Object>of("success", success, "message", msg));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.<String, Object>of("success", false,
                "message", "iyzico bağlantı hatası: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(c -> {
            configRepo.delete(c);
            return ResponseEntity.ok(Map.of("message", c.getDisplayName() + " silindi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- DTO with masked credentials ---
    private Map<String, Object> toMaskedDto(PaymentGatewayConfig c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("code", c.getCode());
        dto.put("displayName", c.getDisplayName());
        dto.put("gatewayProtocol", c.getGatewayProtocol());
        dto.put("bankCode", c.getBankCode());
        dto.put("merchantId", c.getMerchantId());
        dto.put("terminalId", c.getTerminalId());
        dto.put("storeKey", mask(c.getStoreKey()));
        dto.put("provisionPassword", mask(c.getProvisionPassword()));
        dto.put("apiKey", mask(c.getApiKey()));
        dto.put("secretKey", mask(c.getSecretKey()));
        dto.put("baseUrl", c.getBaseUrl());
        dto.put("threeDUrl", c.getThreeDUrl());
        dto.put("callbackUrl", c.getCallbackUrl());
        dto.put("active", c.isActive());
        dto.put("defaultGateway", c.isDefaultGateway());
        dto.put("sandbox", c.isSandbox());
        dto.put("priority", c.getPriority());
        dto.put("supportedCards", c.getSupportedCards());
        dto.put("maxInstallments", c.getMaxInstallments());
        dto.put("createdAt", c.getCreatedAt());
        dto.put("updatedAt", c.getUpdatedAt());
        return dto;
    }

    private boolean hasValue(String s) { return s != null && !s.trim().isEmpty(); }

    private String mask(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() > 8) return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        return "****";
    }
}
