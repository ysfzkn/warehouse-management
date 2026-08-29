package com.warehouse.controller;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoTrackingStatus;
import com.warehouse.service.cargo.KargonomiCargoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Kargonomi webhook receiver. When a Kargonomi shipment status changes,
 * a POST arrives here; the HMAC-SHA256 signature is verified and, if valid,
 * the corresponding Order is updated (eliminating the need for a polling job).
 *
 * <p>Registration on the Kargonomi side:
 * <pre>
 *   POST /webhooks
 *   { name, url: "https://sizinsite.com/api/public/cargo/kargonomi/webhook",
 *     event_type: "shipment.updated", is_active: true, secret: "..." }
 * </pre>
 *
 * <p>Retry policy (Kargonomi): 60s, 120s, 300s, 600s, 1200s — retries until a
 * 2xx is returned. 400/401/403/404/409/410/422 are skipped.
 *
 * <p>Idempotency: the same event is not processed more than once based on
 * payload.meta.idempotency_key (simple in-memory short-window tracking; use Redis/DB in production).
 */
@RestController
@RequestMapping("/api/public/cargo/kargonomi")
@Profile("!test")
public class KargonomiWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KargonomiWebhookController.class);

    private final OrderRepository orderRepository;
    private final CargoApiService cargoApiService;
    private final SiteSettingService settingService;

    // Short-term idempotency cache — last 1000 keys
    private final java.util.concurrent.ConcurrentHashMap<String, Long> seenKeys
            = new java.util.concurrent.ConcurrentHashMap<>();

    public KargonomiWebhookController(OrderRepository orderRepository,
                                       CargoApiService cargoApiService,
                                       SiteSettingService settingService) {
        this.orderRepository = orderRepository;
        this.cargoApiService = cargoApiService;
        this.settingService = settingService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        // 1) HMAC verification — fail CLOSED.
        //
        // This endpoint sits under /api/public/** and is reachable by anyone on the
        // internet; the signature is the only thing that distinguishes the carrier
        // from an attacker. Previously a missing secret meant the check was skipped
        // with nothing but a log line, so any unauthenticated POST could move an
        // order to "delivered". A webhook we cannot authenticate is refused.
        String secret = settingService.getSetting("kargonomi_webhook_secret");
        if (secret == null || secret.isBlank()) {
            log.error("[KargonomiWebhook] kargonomi_webhook_secret tanımsız — webhook REDDEDİLDİ. "
                    + "Ayarlar ekranından secret tanımlanana kadar kargo bildirimleri işlenmeyecek.");
            return ResponseEntity.status(503).body(Map.of("error", "webhook secret not configured"));
        }
        String expected = hmacSha256Hex(secret, rawBody);
        if (signature == null || !constantTimeEquals(expected, signature.trim())) {
            log.warn("[KargonomiWebhook] imza reddedildi — beklenen != gelen");
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }

        // 2) JSON parsing
        Map<String, Object> payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[KargonomiWebhook] JSON parse hatası: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid json"));
        }

        // 3) Idempotency check
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) payload.getOrDefault("meta", Map.of());
        String idemKey = String.valueOf(meta.getOrDefault("idempotency_key", ""));
        if (!idemKey.isBlank() && seenKeys.putIfAbsent(idemKey, System.currentTimeMillis()) != null) {
            log.debug("[KargonomiWebhook] duplicate idem key {} — skip", idemKey);
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
        cleanOldIdemKeys();

        // 4) Extract the shipment
        @SuppressWarnings("unchecked")
        Map<String, Object> shipment = (Map<String, Object>) payload.get("shipment");
        if (shipment == null || shipment.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing shipment"));
        }

        // 5) Find the Order (by providerShipmentId or tracking_code)
        Order order = findOrder(shipment);
        if (order == null) {
            log.info("[KargonomiWebhook] shipment geldi ama eşleşen order yok: {}",
                    shipment.get("id"));
            // Return 200; otherwise Kargonomi will retry repeatedly
            return ResponseEntity.ok(Map.of("status", "order not found"));
        }

        // 6) Parse + apply
        CargoTrackingStatus status = parseWithProvider(shipment);
        boolean changed = cargoApiService.applyTrackingUpdate(order, status);
        log.info("[KargonomiWebhook] order={} status={} changed={}",
                order.getOrderNumber(), status.getStatus(), changed);

        return ResponseEntity.ok(Map.of("status", "ok", "orderNumber", order.getOrderNumber()));
    }

    /** Find the Order from the webhook payload. First by providerShipmentId, then by tracking code. */
    private Order findOrder(Map<String, Object> shipment) {
        String shipmentId = String.valueOf(shipment.getOrDefault("id", ""));
        if (!shipmentId.isBlank()) {
            Optional<Order> byProvider = orderRepository.findByCargoProviderShipmentId(shipmentId);
            if (byProvider.isPresent()) return byProvider.get();
        }
        String trackingCode = String.valueOf(
                shipment.getOrDefault("shipping_webservice_tracking_code", ""));
        if (trackingCode.isBlank()) trackingCode = String.valueOf(shipment.getOrDefault("tracking_code", ""));
        if (!trackingCode.isBlank()) {
            return orderRepository.findByCargoTrackingNo(trackingCode).orElse(null);
        }
        return null;
    }

    /** Shipment map → CargoTrackingStatus. Re-uses the logic in KargonomiCargoProvider. */
    private CargoTrackingStatus parseWithProvider(Map<String, Object> shipment) {
        if (cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider k) {
            return k.parseTrackingResponse(shipment);
        }
        // Fallback minimal parse
        return CargoTrackingStatus.builder()
                .status(CargoTrackingStatus.CargoStatus.UNKNOWN)
                .statusText(String.valueOf(shipment.get("status")))
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC hesaplanamadı: " + e.getMessage(), e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    private void cleanOldIdemKeys() {
        if (seenKeys.size() < 1000) return;
        long cutoff = System.currentTimeMillis() - 10 * 60 * 1000; // older than 10 minutes
        seenKeys.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
