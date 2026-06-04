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
    public ResponseEntity<?> create(
            @RequestBody PaymentGatewayConfig config,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        // Protocol-specific required-field + security validation (HTTPS URLs, etc.)
        try {
            validateConfig(config, false);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        config.setId(null);
        PaymentGatewayConfig saved = configRepo.save(config);
        return ResponseEntity.ok(toMaskedDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody PaymentGatewayConfig update,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        try {
            validateConfig(update, true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
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
            return ResponseEntity.ok((Object) toMaskedDto(configRepo.save(existing)));
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

    /**
     * Default gateway assignment — atomic (race condition is prevented).
     * The previous implementation did two separate save loops; if two admins made
     * different gateways the default at the same time, two records could remain
     * is_default=true. Now we do a bulk UPDATE within a single transaction.
     */
    @PutMapping("/{id}/set-default")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, String>> setDefault(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return configRepo.findById(id).map(c -> {
            // 1) If the target gateway is inactive or missing credentials, it must not be set as default
            if (!isReadyForDefault(c)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Gateway eksik yapılandırılmış (credential veya callback URL). Önce tamamlayın."));
            }
            // 2) Clear all defaults with a single SQL (atomic)
            configRepo.clearAllDefaults();
            // 3) Make the target default + active
            c.setDefaultGateway(true);
            c.setActive(true);
            configRepo.save(c);
            return ResponseEntity.ok(Map.of("message", c.getDisplayName() + " varsayilan gateway olarak ayarlandi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Minimum configuration check before setting as default. */
    private boolean isReadyForDefault(PaymentGatewayConfig c) {
        if (c.getGatewayProtocol() == null) return false;
        String p = c.getGatewayProtocol();
        if ("IYZICO".equals(p)) {
            return hasValue(c.getApiKey()) && hasValue(c.getSecretKey()) && hasValue(c.getBaseUrl());
        }
        if ("PAYTR".equals(p)) {
            return hasValue(c.getMerchantId()) && hasValue(c.getApiKey()) && hasValue(c.getSecretKey())
                    && hasValue(c.getCallbackUrl());
        }
        if ("NESTPAY".equals(p) || "GVP".equals(p)) {
            return hasValue(c.getMerchantId()) && hasValue(c.getTerminalId())
                    && hasValue(c.getStoreKey()) && hasValue(c.getCallbackUrl());
        }
        return false;
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
        // Secrets: both mask (preview) and `*HasValue` flag ("set" state in the UI)
        dto.put("storeKey", mask(c.getStoreKey()));
        dto.put("storeKeySet", hasValue(c.getStoreKey()));
        dto.put("provisionPassword", mask(c.getProvisionPassword()));
        dto.put("provisionPasswordSet", hasValue(c.getProvisionPassword()));
        dto.put("apiKey", mask(c.getApiKey()));
        dto.put("apiKeySet", hasValue(c.getApiKey()));
        dto.put("secretKey", mask(c.getSecretKey()));
        dto.put("secretKeySet", hasValue(c.getSecretKey()));
        dto.put("baseUrl", c.getBaseUrl());
        dto.put("threeDUrl", c.getThreeDUrl());
        dto.put("callbackUrl", c.getCallbackUrl());
        dto.put("active", c.isActive());
        dto.put("defaultGateway", c.isDefaultGateway());
        dto.put("sandbox", c.isSandbox());
        dto.put("priority", c.getPriority());
        dto.put("supportedCards", c.getSupportedCards());
        dto.put("maxInstallments", c.getMaxInstallments());
        // Fields such as merchant_ok_url, merchant_fail_url, timeout_limit for PayTR live here
        dto.put("extraConfig", c.getExtraConfig());
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

    /**
     * Protocol-specific validation + security checks.
     *
     * All URLs sent to production must be HTTPS (localhost http:// is allowed in
     * sandbox mode). Required credentials differ per protocol.
     *
     * @param config the gateway to validate
     * @param isUpdate if true, secrets may arrive empty (existing ones are preserved);
     *                 if false (create), required secrets must be present
     */
    private void validateConfig(PaymentGatewayConfig config, boolean isUpdate) {
        if (config == null) throw new IllegalArgumentException("Gateway yapılandırması boş.");
        String protocol = config.getGatewayProtocol();
        if (!hasValue(protocol)) throw new IllegalArgumentException("Protokol seçilmedi.");
        if (!hasValue(config.getCode())) throw new IllegalArgumentException("Gateway kodu zorunludur.");
        if (!hasValue(config.getDisplayName())) throw new IllegalArgumentException("Görünen ad zorunludur.");

        boolean sandbox = config.isSandbox();

        // URL check — strictly HTTPS in production; http://localhost OK in sandbox
        validateUrl("Callback URL", config.getCallbackUrl(), sandbox, true);
        validateUrl("3D Secure URL", config.getThreeDUrl(), sandbox, false);
        validateUrl("Base URL", config.getBaseUrl(), sandbox, false);

        // Protocol-specific required fields
        switch (protocol) {
            case "IYZICO" -> {
                if (!isUpdate) {
                    if (!hasValue(config.getApiKey()))
                        throw new IllegalArgumentException("Iyzico için API Key zorunludur.");
                    if (!hasValue(config.getSecretKey()))
                        throw new IllegalArgumentException("Iyzico için Secret Key zorunludur.");
                }
                if (!hasValue(config.getBaseUrl()))
                    throw new IllegalArgumentException("Iyzico için Base URL zorunludur (örn. https://api.iyzipay.com).");
            }
            case "PAYTR" -> {
                if (!hasValue(config.getMerchantId()))
                    throw new IllegalArgumentException("PayTR için Mağaza No (merchant_id) zorunludur.");
                if (!isUpdate) {
                    if (!hasValue(config.getApiKey()))
                        throw new IllegalArgumentException("PayTR için Mağaza Parolası (merchant_key) zorunludur.");
                    if (!hasValue(config.getSecretKey()))
                        throw new IllegalArgumentException("PayTR için Gizli Anahtar (merchant_salt) zorunludur.");
                }
                if (!hasValue(config.getCallbackUrl()))
                    throw new IllegalArgumentException("PayTR için Callback URL (notify_url) zorunludur.");
                // PayTR's extraConfig must also contain merchant_ok_url / merchant_fail_url
                Map<String, Object> extra = config.getExtraConfig();
                if (extra != null) {
                    Object okUrl = extra.get("merchant_ok_url");
                    Object failUrl = extra.get("merchant_fail_url");
                    if (okUrl instanceof String s && !s.isBlank()) validateUrl("Başarı URL", s, sandbox, true);
                    if (failUrl instanceof String s && !s.isBlank()) validateUrl("Hata URL", s, sandbox, true);
                }
            }
            case "NESTPAY", "GVP" -> {
                if (!hasValue(config.getMerchantId()))
                    throw new IllegalArgumentException(protocol + " için Merchant ID zorunludur.");
                if (!hasValue(config.getTerminalId()))
                    throw new IllegalArgumentException(protocol + " için Terminal ID zorunludur.");
                if (!isUpdate && !hasValue(config.getStoreKey()))
                    throw new IllegalArgumentException(protocol + " için Store Key zorunludur.");
                if ("GVP".equals(protocol) && !isUpdate && !hasValue(config.getProvisionPassword()))
                    throw new IllegalArgumentException("GVP için Provision Password zorunludur.");
                if (!hasValue(config.getBankCode()))
                    throw new IllegalArgumentException(protocol + " için banka seçimi zorunludur.");
                if (!hasValue(config.getCallbackUrl()))
                    throw new IllegalArgumentException(protocol + " için Callback URL zorunludur.");
            }
            default -> throw new IllegalArgumentException("Bilinmeyen protokol: " + protocol);
        }
    }

    /**
     * URL validation: must be https:// unless in sandbox; in sandbox all
     * localhost variations (admin.localhost, foo.localhost, etc.),
     * 127.0.0.1, 0.0.0.0, *.local and private IP ranges are accepted.
     */
    private void validateUrl(String label, String url, boolean sandbox, boolean required) {
        if (!hasValue(url)) {
            if (required) throw new IllegalArgumentException(label + " zorunludur.");
            return;
        }
        String lower = url.trim().toLowerCase();
        if (lower.startsWith("https://")) return;
        if (!sandbox) {
            throw new IllegalArgumentException(label + " production modunda HTTPS olmalıdır (örn. https://...).");
        }
        // Sandbox: allow local development hosts
        boolean isLocal =
                // Plain localhost and its subdomains: localhost, admin.localhost, foo.bar.localhost
                lower.matches("^http://([a-z0-9-]+\\.)*localhost(:\\d+)?(/|$|\\?).*")
                // Loopback IPs
                || lower.matches("^http://127\\.\\d+\\.\\d+\\.\\d+(:\\d+)?(/|$|\\?).*")
                || lower.startsWith("http://0.0.0.0")
                // .local (mDNS / Bonjour)
                || lower.matches("^http://[a-z0-9.-]+\\.local(:\\d+)?(/|$|\\?).*")
                // Private IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x) — for LAN testing
                || lower.matches("^http://192\\.168\\.\\d+\\.\\d+(:\\d+)?(/|$|\\?).*")
                || lower.matches("^http://10\\.\\d+\\.\\d+\\.\\d+(:\\d+)?(/|$|\\?).*")
                || lower.matches("^http://172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+(:\\d+)?(/|$|\\?).*");
        if (!isLocal) {
            throw new IllegalArgumentException(label + " sandbox modunda bile HTTPS önerilir; "
                    + "lokal test için http://localhost, http://*.localhost, http://192.168.x.x, "
                    + "http://10.x.x.x veya http://...local izinli.");
        }
    }
}
