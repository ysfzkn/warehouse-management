package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kargonomi Cargo API entegrasyonu.
 *
 * Kargonomi multi-carrier aggregator'dır: Yurtiçi, Aras, MNG, PTT gibi
 * tüm firmaları tek API üzerinden yönetir.
 *
 * Resmi dokümantasyon: https://www.kargonomi.com.tr/help/api-dokumantasyonu/kargonomi-api/
 *
 * Yapılandırma (site_settings tablosundan):
 * - cargo_api_provider = "KARGONOMI"
 * - kargonomi_api_token (Bearer token)
 * - kargonomi_app_key (APP KEY header)
 * - kargonomi_api_base_url (default: https://app.kargonomi.com.tr/api/v1)
 *
 * NOT: Bu implementasyon API dokümantasyonunun detaylı alan bilgileri
 * elde edildiğinde tamamlanmalıdır. Request/response mapping'i
 * gerçek API spec'ine göre güncelleyin.
 */
@Component
public class KargonomiCargoProvider implements CargoApiProvider {

    private static final Logger logger = LoggerFactory.getLogger(KargonomiCargoProvider.class);
    private static final String DEFAULT_BASE_URL = "https://app.kargonomi.com.tr/api/v1";

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate;

    public KargonomiCargoProvider(SiteSettingService settingService) {
        this.settingService = settingService;
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

    @Override
    public CargoShipmentResult createShipment(CargoShipmentRequest request) {
        if (!isEnabled()) {
            return CargoShipmentResult.failure("NOT_CONFIGURED", "Kargonomi API yapılandırılmamış");
        }

        try {
            String url = getBaseUrl() + "/shipments";

            // Kargonomi API body yapısı (dokümantasyonu alınca kesinleştirilecek)
            Map<String, Object> body = buildShipmentBody(request);

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> respBody = response.getBody();
            if (respBody == null) {
                return CargoShipmentResult.failure("EMPTY_RESPONSE", "Kargonomi boş yanıt döndürdü");
            }

            // Response parse — dokümantasyonu alınca kesinleştirilecek
            return parseShipmentResponse(respBody);
        } catch (HttpClientErrorException e) {
            logger.error("Kargonomi shipment create hatası: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return CargoShipmentResult.failure(
                String.valueOf(e.getStatusCode().value()),
                "Kargonomi hatası: " + e.getResponseBodyAsString()
            );
        } catch (Exception e) {
            logger.error("Kargonomi shipment create exception: {}", e.getMessage(), e);
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    @Override
    public CargoTrackingStatus getTrackingStatus(String trackingNumber) {
        if (!isEnabled() || trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }

        try {
            String url = getBaseUrl() + "/shipments?tracking_code=" + trackingNumber;
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            // Response parsing — dokümantasyona göre kesinleştirilecek
            return parseTrackingResponse(trackingNumber, body);
        } catch (Exception e) {
            logger.warn("Kargonomi tracking sorgulama hatası: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public CargoShipmentResult cancelShipment(String providerShipmentId) {
        if (!isEnabled() || providerShipmentId == null || providerShipmentId.isBlank()) {
            return CargoShipmentResult.failure("INVALID", "Geçersiz shipment ID");
        }

        try {
            String url = getBaseUrl() + "/shipments/" + providerShipmentId + "/cancel";
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            return CargoShipmentResult.builder()
                    .success(response.getStatusCode().is2xxSuccessful())
                    .providerShipmentId(providerShipmentId)
                    .build();
        } catch (Exception e) {
            logger.error("Kargonomi cancel hatası: {}", e.getMessage());
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    @Override
    public CargoShipmentResult createReturnShipment(CargoShipmentRequest request) {
        // Kargonomi'de iade için is_return flag'i ile createShipment kullanılır
        Map<String, Object> body = buildShipmentBody(request);
        body.put("is_return", true);

        try {
            String url = getBaseUrl() + "/shipments";
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> respBody = response.getBody();
            if (respBody == null) {
                return CargoShipmentResult.failure("EMPTY_RESPONSE", "Boş yanıt");
            }
            return parseShipmentResponse(respBody);
        } catch (Exception e) {
            logger.error("Kargonomi return shipment hatası: {}", e.getMessage());
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    // === Private helpers ===

    private String getBaseUrl() {
        String url = settingService.getSetting("kargonomi_api_base_url");
        if (url == null || url.isBlank()) url = DEFAULT_BASE_URL;
        return url.replaceAll("/+$", "");
    }

    private HttpHeaders buildAuthHeaders() {
        String token = settingService.getSetting("kargonomi_api_token");
        String appKey = settingService.getSetting("kargonomi_app_key");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("APP-KEY", appKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Kargonomi shipment create body.
     *
     * NOT: Bu alan isimleri Kargonomi'nin resmi API dokümantasyonu elde
     * edildiğinde kesinleştirilmelidir. Yaygın Kargonomi field pattern'i
     * temel alınarak hazırlanmıştır.
     */
    private Map<String, Object> buildShipmentBody(CargoShipmentRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        // Alıcı
        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("name", request.getRecipientName());
        receiver.put("phone", request.getRecipientPhone());
        receiver.put("email", request.getRecipientEmail());
        receiver.put("address", request.getRecipientAddress());
        receiver.put("city", request.getRecipientCity());
        receiver.put("district", request.getRecipientDistrict());
        receiver.put("postal_code", request.getRecipientPostalCode());
        body.put("receiver", receiver);

        // Gönderici
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", request.getSenderName());
        sender.put("phone", request.getSenderPhone());
        sender.put("address", request.getSenderAddress());
        sender.put("city", request.getSenderCity());
        sender.put("district", request.getSenderDistrict());
        sender.put("postal_code", request.getSenderPostalCode());
        body.put("sender", sender);

        // Paket
        body.put("package_count", request.getPackageCount() != null ? request.getPackageCount() : 1);
        if (request.getTotalWeightKg() != null) body.put("weight", request.getTotalWeightKg());
        if (request.getTotalDesi() != null) body.put("desi", request.getTotalDesi());
        if (request.getContentDescription() != null) body.put("description", request.getContentDescription());

        // Sipariş bilgileri
        body.put("order_number", request.getOrderNumber());
        if (request.getOrderAmount() != null) body.put("order_amount", request.getOrderAmount());

        // Kapıda ödeme
        if (request.isCashOnDelivery()) {
            body.put("cash_on_delivery", true);
            body.put("cod_amount", request.getCashOnDeliveryAmount());
        }

        // Taşıyıcı tercihi
        if (request.getPreferredCarrier() != null) {
            body.put("shipping_provider_slug", request.getPreferredCarrier());
        }
        if (request.getServiceType() != null) {
            body.put("service_type", request.getServiceType());
        }

        if (request.getDeliveryNote() != null) {
            body.put("delivery_note", request.getDeliveryNote());
        }

        return body;
    }

    /**
     * Kargonomi shipment response'u parse eder.
     * Response şu alanları içerir (arama sonuçlarından):
     * - shipping_webservice_tracking_code
     * - shipping_provider_name
     * - shipping_provider_slug
     * - status
     */
    @SuppressWarnings("unchecked")
    private CargoShipmentResult parseShipmentResponse(Map<String, Object> respBody) {
        // Bazı API'ler data wrapper kullanır: {"data": {...}}
        Object data = respBody.getOrDefault("data", respBody);
        Map<String, Object> shipment = data instanceof Map ? (Map<String, Object>) data : respBody;

        String trackingCode = strVal(shipment.get("shipping_webservice_tracking_code"));
        String providerName = strVal(shipment.get("shipping_provider_name"));
        String providerSlug = strVal(shipment.get("shipping_provider_slug"));
        String id = strVal(shipment.get("id"));
        String labelUrl = strVal(shipment.get("label_url"));

        if (trackingCode == null || trackingCode.isBlank()) {
            return CargoShipmentResult.failure("NO_TRACKING", "Takip numarası alınamadı");
        }

        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingCode)
                .trackingUrl(buildCarrierTrackingUrl(providerSlug, trackingCode))
                .carrierName(providerName)
                .carrierCode(providerSlug)
                .providerShipmentId(id)
                .labelUrl(labelUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    private CargoTrackingStatus parseTrackingResponse(String trackingNumber, Map<String, Object> respBody) {
        Object data = respBody.getOrDefault("data", respBody);
        Map<String, Object> shipment;
        if (data instanceof List && !((List<?>) data).isEmpty()) {
            shipment = (Map<String, Object>) ((List<?>) data).get(0);
        } else if (data instanceof Map) {
            shipment = (Map<String, Object>) data;
        } else {
            return null;
        }

        String statusText = strVal(shipment.get("status"));
        CargoTrackingStatus.CargoStatus status = mapStatus(statusText);
        LocalDateTime deliveredAt = parseDate(strVal(shipment.get("delivered_at")));

        // Events / movements
        List<CargoTrackingStatus.TrackingEvent> events = new ArrayList<>();
        Object movements = shipment.get("movements");
        if (movements instanceof List) {
            for (Object m : (List<?>) movements) {
                if (m instanceof Map) {
                    Map<String, Object> mov = (Map<String, Object>) m;
                    events.add(CargoTrackingStatus.TrackingEvent.builder()
                            .timestamp(parseDate(strVal(mov.get("created_at"))))
                            .description(strVal(mov.get("description")))
                            .location(strVal(mov.get("location")))
                            .status(mapStatus(strVal(mov.get("status"))))
                            .build());
                }
            }
        }

        return CargoTrackingStatus.builder()
                .trackingNumber(trackingNumber)
                .status(status)
                .statusText(statusText)
                .deliveredAt(deliveredAt)
                .events(events)
                .build();
    }

    private String buildCarrierTrackingUrl(String slug, String trackingCode) {
        if (slug == null || trackingCode == null) return null;
        // Yaygın taşıyıcıların public tracking URL pattern'leri
        return switch (slug.toLowerCase()) {
            case "yurtici" -> "https://www.yurticikargo.com/tr/online-servisler/gonderi-sorgula?code=" + trackingCode;
            case "aras" -> "https://kargotakip.araskargo.com.tr/?code=" + trackingCode;
            case "mng" -> "https://service.mngkargo.com.tr/iShipmentWS/TrackShipments?trackingNumber=" + trackingCode;
            case "ptt" -> "https://gonderitakip.ptt.gov.tr/Track/summary?q=" + trackingCode;
            default -> "https://app.kargonomi.com.tr/tracking/" + trackingCode;
        };
    }

    private CargoTrackingStatus.CargoStatus mapStatus(String statusText) {
        if (statusText == null) return CargoTrackingStatus.CargoStatus.UNKNOWN;
        String s = statusText.toUpperCase().trim();
        if (s.contains("DELIVERED") || s.contains("TESLIM EDILDI") || s.contains("TESLİM EDİLDİ")) {
            return CargoTrackingStatus.CargoStatus.DELIVERED;
        }
        if (s.contains("CREATED") || s.contains("OLUSTURULDU") || s.contains("OLUŞTURULDU")) {
            return CargoTrackingStatus.CargoStatus.CREATED;
        }
        if (s.contains("PICKED_UP") || s.contains("TESLIM_ALINDI")) {
            return CargoTrackingStatus.CargoStatus.PICKED_UP;
        }
        if (s.contains("OUT_FOR_DELIVERY") || s.contains("DAGITIMDA") || s.contains("DAĞITIMDA")) {
            return CargoTrackingStatus.CargoStatus.OUT_FOR_DELIVERY;
        }
        if (s.contains("TRANSIT") || s.contains("YOLDA")) {
            return CargoTrackingStatus.CargoStatus.IN_TRANSIT;
        }
        if (s.contains("RETURN")) {
            return CargoTrackingStatus.CargoStatus.RETURNED;
        }
        if (s.contains("CANCELLED") || s.contains("IPTAL") || s.contains("İPTAL")) {
            return CargoTrackingStatus.CargoStatus.CANCELLED;
        }
        return CargoTrackingStatus.CargoStatus.UNKNOWN;
    }

    private String strVal(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}
        try {
            // "2026-04-17 14:30:00" format
            return LocalDateTime.parse(dateStr.replace(" ", "T"));
        } catch (Exception ignored) {}
        try {
            // "2026-04-17T14:30:00.000Z" format
            return LocalDateTime.parse(dateStr.replace("Z", "").replaceAll("\\.\\d+$", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
