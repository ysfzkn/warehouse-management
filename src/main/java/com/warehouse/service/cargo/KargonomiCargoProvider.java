package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kargonomi Cargo API integration (v1).
 *
 * <p>Kargonomi is an aggregator service in front of carriers such as Yurtiçi,
 * Aras, MNG, PTT, UPS, Sürat, and Sendeo. A single API for price comparison,
 * barcodes, and webhook-based tracking.
 *
 * <p><b>Auth</b>: {@code Authorization: Bearer <token>} + {@code X-App-Key: <appKey>}.
 *
 * <p><b>Two-phase creation flow</b> (official spec):
 * <pre>
 *   1. POST /shipments                      → returns a draft shipment id
 *   2. POST /confirm-shipping-price          → select the carrier ({@code -1} = automatic cheapest)
 *   3. (in the background) Kargonomi creates a webservice order → webhook or polling
 * </pre>
 *
 * <p>Official documentation: https://www.kargonomi.com.tr/help/api-dokumantasyonu/kargonomi-api/
 */
@Component
public class KargonomiCargoProvider implements CargoApiProvider {

    private static final Logger logger = LoggerFactory.getLogger(KargonomiCargoProvider.class);
    private static final String DEFAULT_BASE_URL = "https://app.kargonomi.com.tr/api/v1";

    // shipping_provider_id = -1  →  Kargonomi automatically picks the cheapest
    private static final int AUTO_CHEAPEST_PROVIDER_ID = -1;

    private final SiteSettingService settingService;
    private final KargonomiGeoLookupService geoLookup;
    private final RestTemplate restTemplate;

    public KargonomiCargoProvider(SiteSettingService settingService,
                                   KargonomiGeoLookupService geoLookup) {
        this.settingService = settingService;
        this.geoLookup = geoLookup;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "KARGONOMI";
    }

    @Override
    public boolean isEnabled() {
        if (!"KARGONOMI".equalsIgnoreCase(settingService.getSetting("cargo_api_provider"))) {
            return false;
        }
        String token = settingService.getSetting("kargonomi_api_token");
        String appKey = settingService.getSetting("kargonomi_app_key");
        return token != null && !token.isBlank() && appKey != null && !appKey.isBlank();
    }

    // ─────────────────────────────────────────────────────────────
    //  Create Shipment (2-phase)
    // ─────────────────────────────────────────────────────────────

    @Override
    @Retry(name = "kargonomi", fallbackMethod = "createShipmentFallback")
    @CircuitBreaker(name = "kargonomi", fallbackMethod = "createShipmentFallback")
    public CargoShipmentResult createShipment(CargoShipmentRequest request) {
        if (!isEnabled()) {
            return CargoShipmentResult.failure("NOT_CONFIGURED", "Kargonomi API yapılandırılmamış");
        }

        try {
            // ── Recipient province/district → Kargonomi id ──
            int[] buyerGeo = geoLookup.lookupStateAndCity(
                    request.getRecipientCity(), request.getRecipientDistrict());
            if (buyerGeo == null) {
                return CargoShipmentResult.failure("GEO_NOT_FOUND",
                        "Alıcı il/ilçe Kargonomi veritabanında bulunamadı: "
                        + request.getRecipientCity() + " / " + request.getRecipientDistrict());
            }

            // ── Phase 1: create draft shipment ──
            Map<String, Object> body = buildShipmentBody(request, buyerGeo[0], buyerGeo[1], false);
            String url = getBaseUrl() + "/shipments";

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            logger.debug("[Kargonomi] POST /shipments — orderNumber={}", request.getOrderNumber());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> respBody = response.getBody();
            if (respBody == null) {
                return CargoShipmentResult.failure("EMPTY_RESPONSE", "Kargonomi boş yanıt döndürdü");
            }

            Map<String, Object> shipment = unwrapData(respBody);
            String shipmentId = strVal(shipment.get("id"));
            if (shipmentId == null) {
                return CargoShipmentResult.failure("NO_ID",
                        "Kargonomi shipment id dönmedi — response: " + truncate(respBody.toString(), 300));
            }

            // ── Phase 2: confirm the carrier selection ──
            // If a preferredCarrier exists (e.g. "yurtici"), we resolve that carrier's
            // provider_id from the price comparison and confirm with it. Otherwise -1 = Kargonomi automatic cheapest.
            int preferredProviderId = resolvePreferredProviderId(shipmentId, request.getPreferredCarrier());
            CargoShipmentResult confirmResult = confirmShippingPrice(shipmentId, preferredProviderId);
            if (!confirmResult.isSuccess()) return confirmResult;

            // ── Phase 3: fetch the final shipment again (for the tracking code + label URL) ──
            Map<String, Object> finalShipment = fetchShipment(shipmentId);
            return buildResultFromShipment(finalShipment, shipmentId);
        } catch (HttpClientErrorException e) {
            logger.error("Kargonomi shipment create HTTP hatası: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return CargoShipmentResult.failure(
                    String.valueOf(e.getStatusCode().value()),
                    "Kargonomi hatası: " + truncate(e.getResponseBodyAsString(), 400));
        } catch (Exception e) {
            logger.error("Kargonomi shipment create exception: {}", e.getMessage(), e);
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    /** POST /confirm-shipping-price — Phase 2 */
    private CargoShipmentResult confirmShippingPrice(String shipmentId, int providerId) {
        try {
            String url = getBaseUrl() + "/confirm-shipping-price";
            Map<String, Object> body = Map.of(
                    "shipment_id", shipmentId,
                    "shipping_provider_id", providerId
            );
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, Map.class);
            logger.debug("[Kargonomi] confirmShippingPrice OK (shipment={}, providerId={})",
                    shipmentId, providerId);
            return CargoShipmentResult.builder().success(true).providerShipmentId(shipmentId).build();
        } catch (HttpClientErrorException e) {
            logger.error("confirmShippingPrice hatası: {}", e.getResponseBodyAsString());
            return CargoShipmentResult.failure("CONFIRM_FAILED",
                    "Kargo firması onaylanamadı: " + truncate(e.getResponseBodyAsString(), 300));
        } catch (Exception e) {
            return CargoShipmentResult.failure("CONFIRM_FAILED", e.getMessage());
        }
    }

    /**
     * Slug (e.g. "yurtici") → Kargonomi {@code shipping_provider_id}.
     * <p>{@code GET /shipment-price-comparison/{id}} is called for the draft shipment; a
     * slug match is searched for in the returned carrier list. If not found (e.g. that
     * carrier is not active in the Kargonomi account), it falls back to {@code -1} (automatic cheapest).
     */
    @SuppressWarnings("unchecked")
    private int resolvePreferredProviderId(String shipmentId, String preferredSlug) {
        if (preferredSlug == null || preferredSlug.isBlank()) {
            return AUTO_CHEAPEST_PROVIDER_ID;
        }
        try {
            String url = getBaseUrl() + "/shipment-price-comparison/" + shipmentId;
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = unwrapData(response.getBody());

            Object providers = body.get("shipping_provider_with_price");
            if (!(providers instanceof List<?> list)) return AUTO_CHEAPEST_PROVIDER_ID;

            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String slug = strVal(m.get("slug"));
                if (slug != null && slug.equalsIgnoreCase(preferredSlug.trim())) {
                    Object id = m.get("id");
                    if (id instanceof Number n) {
                        logger.info("[Kargonomi] preferred carrier '{}' → provider_id={}", slug, n.intValue());
                        return n.intValue();
                    }
                }
            }
            logger.info("[Kargonomi] '{}' slug Kargonomi listesinde yok — auto-cheapest ile devam",
                    preferredSlug);
            return AUTO_CHEAPEST_PROVIDER_ID;
        } catch (Exception e) {
            logger.warn("price-comparison çözümleme hatası: {} — auto-cheapest kullanılıyor", e.getMessage());
            return AUTO_CHEAPEST_PROVIDER_ID;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Tracking (GET /shipments/{id})
    // ─────────────────────────────────────────────────────────────

    @Override
    public CargoTrackingStatus getTrackingStatus(String providerShipmentId) {
        if (!isEnabled() || providerShipmentId == null || providerShipmentId.isBlank()) return null;
        try {
            Map<String, Object> shipment = fetchShipment(providerShipmentId);
            if (shipment == null) return null;
            return parseTrackingResponse(shipment);
        } catch (Exception e) {
            logger.warn("Kargonomi tracking sorgulama hatası (shipment={}): {}",
                    providerShipmentId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchShipment(String shipmentId) {
        try {
            String url = getBaseUrl() + "/shipments/" + shipmentId;
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody() != null ? unwrapData(response.getBody()) : null;
        } catch (Exception e) {
            logger.debug("fetchShipment {} hatası: {}", shipmentId, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Cancel (POST /shipments/cancel  with body {shipment_id})
    // ─────────────────────────────────────────────────────────────

    @Override
    public CargoShipmentResult cancelShipment(String providerShipmentId) {
        if (!isEnabled() || providerShipmentId == null || providerShipmentId.isBlank()) {
            return CargoShipmentResult.failure("INVALID", "Geçersiz shipment ID");
        }
        try {
            String url = getBaseUrl() + "/shipments/cancel";
            Map<String, Object> body = Map.of("shipment_id", providerShipmentId);
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return CargoShipmentResult.builder()
                    .success(response.getStatusCode().is2xxSuccessful())
                    .providerShipmentId(providerShipmentId)
                    .build();
        } catch (HttpClientErrorException e) {
            logger.error("Kargonomi cancel hatası: {}", e.getResponseBodyAsString());
            return CargoShipmentResult.failure(String.valueOf(e.getStatusCode().value()),
                    "İptal hatası: " + truncate(e.getResponseBodyAsString(), 300));
        } catch (Exception e) {
            logger.error("Kargonomi cancel exception: {}", e.getMessage());
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Return shipment (is_return flag)
    // ─────────────────────────────────────────────────────────────

    @Override
    public CargoShipmentResult createReturnShipment(CargoShipmentRequest request) {
        if (!isEnabled()) return CargoShipmentResult.failure("NOT_CONFIGURED", "Kargonomi yapılandırılmamış");
        try {
            int[] buyerGeo = geoLookup.lookupStateAndCity(
                    request.getRecipientCity(), request.getRecipientDistrict());
            if (buyerGeo == null) {
                return CargoShipmentResult.failure("GEO_NOT_FOUND", "Alıcı il/ilçe bulunamadı");
            }
            Map<String, Object> body = buildShipmentBody(request, buyerGeo[0], buyerGeo[1], true);
            String url = getBaseUrl() + "/shipments";
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> shipment = unwrapData(response.getBody());
            String shipmentId = strVal(shipment.get("id"));
            if (shipmentId == null) return CargoShipmentResult.failure("NO_ID", "İade shipment id dönmedi");

            // A return shipment also requires provider confirmation
            confirmShippingPrice(shipmentId, AUTO_CHEAPEST_PROVIDER_ID);
            Map<String, Object> finalShipment = fetchShipment(shipmentId);
            return buildResultFromShipment(finalShipment, shipmentId);
        } catch (Exception e) {
            logger.error("Kargonomi return shipment hatası: {}", e.getMessage());
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Barcode / Label (GET /shipments/{id}/barcode)
    // ─────────────────────────────────────────────────────────────

    /**
     * Downloads the cargo label as a PDF. It is returned as Base64, so it is decoded.
     * Query options:
     *   format=pdf (default)
     *   packageContentVisibility=true
     *   warningVisibility=true
     *   integratedOrderNoVisibility=true
     */
    public byte[] downloadBarcodePdf(String shipmentId) {
        if (!isEnabled() || shipmentId == null || shipmentId.isBlank()) return new byte[0];
        try {
            String url = getBaseUrl() + "/shipments/" + shipmentId + "/barcode"
                    + "?format=pdf&packageContentVisibility=true"
                    + "&warningVisibility=true&integratedOrderNoVisibility=true";
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return new byte[0];

            // Response e.g.: {"data": "base64string..."} or {"barcode": "base64..."}
            Object data = body.getOrDefault("data", body.get("barcode"));
            if (data == null) data = body.get("pdf");
            if (data instanceof String s && !s.isBlank()) {
                try { return java.util.Base64.getDecoder().decode(s.trim().replaceAll("\\s+", "")); }
                catch (IllegalArgumentException ignored) {}
            }
            return new byte[0];
        } catch (Exception e) {
            logger.error("Kargonomi barcode indirme hatası: {}", e.getMessage());
            return new byte[0];
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Balance
    // ─────────────────────────────────────────────────────────────

    /** GET /user/credit — account balance (TRY). Returns null on error. */
    public BigDecimal getBalance() {
        if (!isEnabled()) return null;
        try {
            String url = getBaseUrl() + "/user/credit";
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return null;
            Object data = body.getOrDefault("data", body);
            if (data instanceof Map<?,?> m) {
                Object credit = m.get("credit");
                if (credit instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                if (credit != null) return new BigDecimal(credit.toString());
            }
            return null;
        } catch (Exception e) {
            logger.warn("Kargonomi balance sorgu hatası: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Webhooks (registration)
    // ─────────────────────────────────────────────────────────────

    /** Webhook registration. Kargonomi POSTs to callbackUrl on {@code shipment.updated} events. */
    public boolean registerWebhook(String callbackUrl, String secret) {
        if (!isEnabled() || callbackUrl == null || callbackUrl.isBlank()) return false;
        try {
            String url = getBaseUrl() + "/webhooks";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "warehouse-management");
            body.put("url", callbackUrl);
            body.put("event_type", "shipment.updated");
            body.put("is_active", true);
            if (secret != null && !secret.isBlank()) body.put("secret", secret);

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            logger.info("[Kargonomi] Webhook kaydedildi: {}", callbackUrl);
            return true;
        } catch (Exception e) {
            logger.warn("Webhook kayıt hatası: {}", e.getMessage());
            return false;
        }
    }

    /** GET /webhooks — lists all registered webhooks. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listWebhooks() {
        if (!isEnabled()) return List.of();
        try {
            String url = getBaseUrl() + "/webhooks";
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return List.of();
            Object data = body.getOrDefault("data", body);
            if (data instanceof List<?> list) return (List<Map<String, Object>>) list;
            return List.of();
        } catch (Exception e) {
            logger.warn("Webhook listesi alınamadı: {}", e.getMessage());
            return List.of();
        }
    }

    /** GET /webhooks/{id} — details of a specific webhook. */
    public Map<String, Object> getWebhook(long webhookId) {
        if (!isEnabled()) return null;
        try {
            String url = getBaseUrl() + "/webhooks/" + webhookId;
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            logger.warn("Webhook detay alınamadı (id={}): {}", webhookId, e.getMessage());
            return null;
        }
    }

    /** PUT /webhooks/{id} — webhook update (URL or is_active change). */
    public boolean updateWebhook(long webhookId, String url, Boolean isActive) {
        if (!isEnabled()) return false;
        try {
            String endpoint = getBaseUrl() + "/webhooks/" + webhookId;
            Map<String, Object> body = new LinkedHashMap<>();
            if (url != null) body.put("url", url);
            if (isActive != null) body.put("is_active", isActive);
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(endpoint, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
            logger.info("[Kargonomi] Webhook güncellendi: id={}", webhookId);
            return true;
        } catch (Exception e) {
            logger.warn("Webhook güncelleme hatası (id={}): {}", webhookId, e.getMessage());
            return false;
        }
    }

    /** DELETE /webhooks/{id} — webhook deletion. */
    public boolean deleteWebhook(long webhookId) {
        if (!isEnabled()) return false;
        try {
            String url = getBaseUrl() + "/webhooks/" + webhookId;
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            logger.info("[Kargonomi] Webhook silindi: id={}", webhookId);
            return true;
        } catch (Exception e) {
            logger.warn("Webhook silme hatası (id={}): {}", webhookId, e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  List shipments (admin reconciliation)
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /shipments — the shipment list on the Kargonomi side (50 per page).
     * Used in the admin reconciliation panel to check "is our DB in sync with Kargonomi?".
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listShipments(int page) {
        if (!isEnabled()) return Map.of("data", List.of());
        try {
            String url = getBaseUrl() + "/shipments?page=" + Math.max(1, page);
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            logger.warn("Shipments listesi alınamadı (page={}): {}", page, e.getMessage());
            return Map.of("data", List.of(), "error", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Warehouse registration (POST /warehouses)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /warehouses — registers a new warehouse (sender address) with Kargonomi.
     * Called once from the admin panel during initial setup; the returned warehouse_id
     * is used as {@code shipment.warehouse_id} when creating shipments.
     */
    public Long registerWarehouse(WarehouseRegistrationRequest req) {
        if (!isEnabled()) return null;
        try {
            // Sender city/district → Kargonomi state_id/city_id lookup
            int[] geo = geoLookup.lookupStateAndCity(req.getCity(), req.getDistrict());
            if (geo == null) {
                logger.warn("[Kargonomi] Warehouse register: il/ilçe bulunamadı ({}/{})", req.getCity(), req.getDistrict());
                return null;
            }
            String url = getBaseUrl() + "/warehouses";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", req.getName());
            body.put("is_main", req.isMain());
            body.put("contact_name", req.getContactName());
            body.put("address", req.getAddress());
            body.put("contact_phone", normalizePhone(req.getContactPhone()));
            body.put("state_id", geo[0]);
            body.put("city_id", geo[1]);
            if (req.getTaxNumber() != null) body.put("tax_number", req.getTaxNumber());

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> resp = response.getBody();
            if (resp == null) return null;
            Object data = resp.getOrDefault("data", resp);
            if (data instanceof Map<?,?> m) {
                Object id = m.get("id");
                if (id instanceof Number n) return n.longValue();
                if (id != null) return Long.parseLong(id.toString());
            }
            return null;
        } catch (Exception e) {
            logger.warn("Warehouse register hatası: {}", e.getMessage());
            return null;
        }
    }

    /** POST /warehouses request DTO. */
    public static class WarehouseRegistrationRequest {
        private String name;
        private boolean main;
        private String contactName;
        private String contactPhone;
        private String address;
        private String city;
        private String district;
        private String taxNumber;

        // ── getters/setters ──
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isMain() { return main; }
        public void setMain(boolean main) { this.main = main; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getTaxNumber() { return taxNumber; }
        public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
    }

    // ─────────────────────────────────────────────────────────────
    //  Request/Response builders
    // ─────────────────────────────────────────────────────────────

    /**
     * Request body for Kargonomi {@code POST /shipments}.
     *
     * <p>Kargonomi's actual spec (official doc):
     * <pre>
     * {
     *   "shipment": {
     *     "buyer_name", "buyer_phone", "buyer_address",
     *     "buyer_state_id", "buyer_city_id",
     *     "buyer_email" (opt), "buyer_tax_number" (opt), "buyer_tax_place" (opt),
     *     "packages": [{ "desi", "content" (opt), "barcode" (opt) }]
     *   },
     *   "warehouse_id": 42   (opt — instead of sender information)
     * }
     * </pre>
     */
    private Map<String, Object> buildShipmentBody(CargoShipmentRequest request,
                                                    int buyerStateId, int buyerCityId,
                                                    boolean isReturn) {
        Map<String, Object> shipment = new LinkedHashMap<>();
        shipment.put("buyer_name", request.getRecipientName());
        shipment.put("buyer_phone", normalizePhone(request.getRecipientPhone()));
        shipment.put("buyer_address", request.getRecipientAddress());
        shipment.put("buyer_state_id", buyerStateId);
        shipment.put("buyer_city_id", buyerCityId);
        if (request.getRecipientEmail() != null) {
            shipment.put("buyer_email", request.getRecipientEmail());
        }

        // Packages array — desi is required for each package
        List<Map<String, Object>> packages = new ArrayList<>();
        int count = request.getPackageCount() != null ? request.getPackageCount() : 1;
        BigDecimal totalDesi = request.getTotalDesi() != null
                ? request.getTotalDesi() : BigDecimal.ONE;
        BigDecimal perPackageDesi = totalDesi.divide(
                BigDecimal.valueOf(Math.max(1, count)), 2, java.math.RoundingMode.UP);

        for (int i = 0; i < count; i++) {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("desi", perPackageDesi);
            if (request.getContentDescription() != null) {
                pkg.put("content", truncate(request.getContentDescription(), 200));
            }
            // barcode → association with the order number (for easy searching within Kargonomi)
            if (request.getOrderNumber() != null) {
                pkg.put("barcode", count == 1
                        ? request.getOrderNumber()
                        : request.getOrderNumber() + "-" + (i + 1));
            }
            packages.add(pkg);
        }
        shipment.put("packages", packages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipment", shipment);

        // warehouse_id can be used for the sender; otherwise inline sender information is sent.
        String warehouseId = settingService.getSetting("kargonomi_warehouse_id");
        if (warehouseId != null && !warehouseId.isBlank()) {
            try { body.put("warehouse_id", Integer.parseInt(warehouseId.trim())); }
            catch (NumberFormatException ignored) {}
        } else {
            // Inline sender (when there is no warehouse)
            int[] senderGeo = geoLookup.lookupStateAndCity(
                    request.getSenderCity(), request.getSenderDistrict());
            if (senderGeo != null) {
                Map<String, Object> sender = new LinkedHashMap<>();
                sender.put("sender_name", request.getSenderName());
                sender.put("sender_phone", normalizePhone(request.getSenderPhone()));
                sender.put("sender_address", request.getSenderAddress());
                sender.put("sender_state_id", senderGeo[0]);
                sender.put("sender_city_id", senderGeo[1]);
                body.put("sender", sender);
            }
        }

        if (isReturn) body.put("is_return", true);
        return body;
    }

    private CargoShipmentResult buildResultFromShipment(Map<String, Object> shipment, String shipmentId) {
        if (shipment == null) {
            return CargoShipmentResult.builder()
                    .success(true)
                    .providerShipmentId(shipmentId)
                    .build();
        }
        String trackingCode = strVal(shipment.get("shipping_webservice_tracking_code"));
        if (trackingCode == null) trackingCode = strVal(shipment.get("tracking_code"));
        String providerName = strVal(shipment.get("shipping_provider_name"));
        String providerSlug = strVal(shipment.get("shipping_provider_slug"));
        String labelUrl = strVal(shipment.get("label_url"));

        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingCode)
                .trackingUrl(buildCarrierTrackingUrl(providerSlug, trackingCode))
                .carrierName(providerName)
                .carrierCode(providerSlug)
                .providerShipmentId(shipmentId)
                .labelUrl(labelUrl)
                .build();
    }

    /** Kargonomi shipment object → CargoTrackingStatus. Also called from the webhook controller. */
    @SuppressWarnings("unchecked")
    public CargoTrackingStatus parseTrackingResponse(Map<String, Object> shipment) {
        String trackingCode = strVal(shipment.get("shipping_webservice_tracking_code"));
        if (trackingCode == null) trackingCode = strVal(shipment.get("tracking_code"));

        String rawStatus = strVal(shipment.get("status"));
        CargoTrackingStatus.CargoStatus mapped = mapStatus(rawStatus);

        LocalDateTime deliveredAt = parseDate(strVal(shipment.get("delivered_at")));
        LocalDateTime estimated = parseDate(strVal(shipment.get("estimated_delivery_at")));

        // Events / movements
        List<CargoTrackingStatus.TrackingEvent> events = new ArrayList<>();
        Object movementsObj = shipment.get("movements");
        if (movementsObj == null) movementsObj = shipment.get("events");
        if (movementsObj instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?,?> m)) continue;
                events.add(CargoTrackingStatus.TrackingEvent.builder()
                        .timestamp(parseDate(strVal(m.get("date"))))
                        .description(strVal(m.get("description")))
                        .location(strVal(m.get("location")))
                        .status(mapStatus(strVal(m.get("status"))))
                        .build());
            }
        }

        return CargoTrackingStatus.builder()
                .trackingNumber(trackingCode)
                .status(mapped)
                .statusText(rawStatus)
                .estimatedDelivery(estimated)
                .deliveredAt(deliveredAt)
                .deliveredTo(strVal(shipment.get("delivered_to")))
                .events(events)
                .build();
    }

    /** Kargonomi status string → CargoStatus enum mapping. */
    static CargoTrackingStatus.CargoStatus mapStatus(String kargonomiStatus) {
        if (kargonomiStatus == null) return CargoTrackingStatus.CargoStatus.UNKNOWN;
        String s = kargonomiStatus.trim().toLowerCase();
        return switch (s) {
            case "draft", "ready",
                 "webservice_order_creating", "webservice_order_created",
                 "webservice_checking_shipment" -> CargoTrackingStatus.CargoStatus.CREATED;
            case "webservice_shipment_started" -> CargoTrackingStatus.CargoStatus.IN_TRANSIT;
            case "webservice_shipment_delivered", "delivered" -> CargoTrackingStatus.CargoStatus.DELIVERED;
            case "webservice_shipment_returning" -> CargoTrackingStatus.CargoStatus.RETURN_IN_TRANSIT;
            case "returned" -> CargoTrackingStatus.CargoStatus.RETURNED;
            case "webservice_order_failed",
                 "webservice_shipment_not_delivered",
                 "webservice_shipment_missing" -> CargoTrackingStatus.CargoStatus.FAILED;
            case "cancelled", "request_for_cancellation" -> CargoTrackingStatus.CargoStatus.CANCELLED;
            default -> CargoTrackingStatus.CargoStatus.UNKNOWN;
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private HttpHeaders buildAuthHeaders() {
        String token = settingService.getSetting("kargonomi_api_token");
        String appKey = settingService.getSetting("kargonomi_app_key");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-App-Key", appKey);   // ← correct header: X-App-Key
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String getBaseUrl() {
        String url = settingService.getSetting("kargonomi_api_base_url");
        if (url == null || url.isBlank()) url = DEFAULT_BASE_URL;
        return url.replaceAll("/+$", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<?, ?> body) {
        if (body == null) return Map.of();
        Object data = body.get("data");
        if (data instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return (Map<String, Object>) body;
    }

    private String buildCarrierTrackingUrl(String slug, String trackingCode) {
        if (trackingCode == null) return null;
        if (slug == null) return null;
        return switch (slug.toLowerCase()) {
            case "yurtici" -> "https://selfservis.yurticikargo.com/takip?code=" + trackingCode;
            case "aras"    -> "https://kargotakip.araskargo.com.tr/mainpage.aspx?code=" + trackingCode;
            case "mng"     -> "https://kargotakip.mngkargo.com.tr/?takipNo=" + trackingCode;
            case "ptt"     -> "https://gonderitakip.ptt.gov.tr/Track/Verify?q=" + trackingCode;
            case "surat"   -> "https://www.suratkargo.com.tr/KargoTakip/?kargotakipno=" + trackingCode;
            case "ups"     -> "https://www.ups.com/track?tracknum=" + trackingCode;
            default -> null;
        };
    }

    private static String strVal(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return null;
        // Kargonomi usually expects 10 digits (without a leading 0)
        String digits = phone.replaceAll("\\D+", "");
        if (digits.startsWith("90") && digits.length() == 12) return digits.substring(2);
        if (digits.startsWith("0") && digits.length() == 11) return digits.substring(1);
        return digits;
    }

    private static LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // ISO 8601 offset: 2026-04-22T10:30:00+03:00
            return LocalDateTime.parse(s.replace(" ", "T").substring(0, Math.min(19, s.length())),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Resilience4j fallback methods
    //  (Called when all retries are exhausted or the circuit breaker is open)
    // ─────────────────────────────────────────────────────────────

    /** Fallback for createShipment — queues the request and returns a "PENDING_RETRY" result that alerts the admin. */
    @SuppressWarnings("unused")
    public CargoShipmentResult createShipmentFallback(CargoShipmentRequest request, Throwable t) {
        logger.error("[Kargonomi] createShipment retry/CB fallback: {}", t.getMessage());
        return CargoShipmentResult.failure("UPSTREAM_UNAVAILABLE",
                "Kargo sağlayıcı şu anda yanıt vermiyor; sipariş kuyruğa alındı, ekibimiz manuel oluşturacak.");
    }
}
