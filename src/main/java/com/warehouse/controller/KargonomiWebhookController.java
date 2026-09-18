package com.warehouse.controller;

import com.warehouse.entity.CargoWebhookDelivery;
import com.warehouse.entity.Order;
import com.warehouse.repository.CargoWebhookDeliveryRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoTrackingStatus;
import com.warehouse.service.cargo.KargonomiCargoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
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
 * <p><b>Idempotency</b> is a unique constraint on {@code cargo_webhook_deliveries}, claimed
 * before any work is done. A delivery that was already handled is acknowledged and ignored; one
 * whose processing failed is allowed through again, because Kargonomi's retry is the only chance
 * that event gets. Every payload is stored either way — that log is what makes a signature or
 * parsing problem diagnosable after the fact.
 */
@RestController
@RequestMapping("/api/public/cargo/kargonomi")
@Profile("!test")
public class KargonomiWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KargonomiWebhookController.class);

    /** Enough of the payload to debug with; a runaway body cannot bloat the table. */
    private static final int MAX_STORED_PAYLOAD = 20_000;

    private final OrderRepository orderRepository;
    private final CargoApiService cargoApiService;
    private final SiteSettingService settingService;
    private final CargoWebhookDeliveryRepository deliveryRepository;

    public KargonomiWebhookController(OrderRepository orderRepository,
                                       CargoApiService cargoApiService,
                                       SiteSettingService settingService,
                                       CargoWebhookDeliveryRepository deliveryRepository) {
        this.orderRepository = orderRepository;
        this.cargoApiService = cargoApiService;
        this.settingService = settingService;
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * Reachability probe. Kargonomi fetches the callback URL before it will save a webhook and
     * refuses the registration with "Belirtilen URL erişilebilir değil" unless it gets a 2xx.
     *
     * <p>The answer is deliberately a constant. Anyone on the internet can call this, so it must
     * not report whether a signing key is configured, which orders exist, or anything else that
     * would differ between two installations — the probe only needs to know the address answers.
     * Delivery of actual events remains POST-only and signature-verified.
     */
    @RequestMapping(value = "/webhook", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Map<String, String>> probe() {
        return ResponseEntity.ok(Map.of("status", "ok"));
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

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) payload.getOrDefault("meta", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> shipment = (Map<String, Object>) payload.get("shipment");

        // 3) Claim the delivery — the database decides whether this event is ours to process.
        CargoWebhookDelivery delivery;
        try {
            delivery = claimDelivery(meta, shipment, rawBody);
        } catch (DuplicateDeliveryException duplicate) {
            log.debug("[KargonomiWebhook] duplicate idem key {} — skip", duplicate.key);
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        // 4) The shipment body itself
        if (shipment == null || shipment.isEmpty()) {
            finish(delivery, CargoWebhookDelivery.STATUS_FAILED, "missing shipment", null);
            return ResponseEntity.badRequest().body(Map.of("error", "missing shipment"));
        }

        try {
            // 5) Find the Order (by providerShipmentId or tracking_code)
            Order order = findOrder(shipment);
            if (order == null) {
                log.info("[KargonomiWebhook] shipment geldi ama eşleşen order yok: {}",
                        shipment.get("id"));
                finish(delivery, CargoWebhookDelivery.STATUS_ORDER_NOT_FOUND, null, null);
                // Return 200; otherwise Kargonomi will retry repeatedly
                return ResponseEntity.ok(Map.of("status", "order not found"));
            }

            // 6) Parse + apply — delivery, failed delivery and returns are all handled there.
            CargoTrackingStatus status = parseWithProvider(shipment);
            boolean changed = cargoApiService.applyTrackingUpdate(
                    order.getId(), status, CargoApiService.SOURCE_WEBHOOK);
            log.info("[KargonomiWebhook] order={} status={} changed={}",
                    order.getOrderNumber(), status.getStatus(), changed);

            finish(delivery, CargoWebhookDelivery.STATUS_PROCESSED, null, order);
            return ResponseEntity.ok(Map.of("status", "ok", "orderNumber", order.getOrderNumber()));

        } catch (Exception e) {
            // Mark it failed rather than processed, so Kargonomi's next retry is let through
            // instead of being dismissed as a duplicate of an event we never actually handled.
            log.error("[KargonomiWebhook] işlenemedi: {}", e.toString(), e);
            finish(delivery, CargoWebhookDelivery.STATUS_FAILED, e.toString(), null);
            return ResponseEntity.status(500).body(Map.of("error", "processing failed"));
        }
    }

    /**
     * Reserves this event for processing.
     *
     * @throws DuplicateDeliveryException if it was already handled — including when a concurrent
     *         request won the race for the same key
     */
    private CargoWebhookDelivery claimDelivery(Map<String, Object> meta,
                                                Map<String, Object> shipment,
                                                String rawBody) {
        String key = String.valueOf(meta.getOrDefault("idempotency_key", "")).trim();
        if (key.isBlank() || "null".equals(key)) {
            // No key from the sender: fall back to the body's own fingerprint, which at least
            // collapses identical retries.
            key = "body:" + sha256Hex(rawBody);
        }

        Optional<CargoWebhookDelivery> existing = deliveryRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            CargoWebhookDelivery previous = existing.get();
            if (!CargoWebhookDelivery.STATUS_FAILED.equals(previous.getStatus())) {
                throw new DuplicateDeliveryException(key);
            }
            previous.setReceivedAt(LocalDateTime.now());
            previous.setStatus(CargoWebhookDelivery.STATUS_RECEIVED);
            return deliveryRepository.save(previous);
        }

        CargoWebhookDelivery row = new CargoWebhookDelivery();
        row.setIdempotencyKey(key);
        row.setStatus(CargoWebhookDelivery.STATUS_RECEIVED);
        row.setEventType(eventTypeOf(meta));
        row.setAttemptNumber(intOrNull(meta.get("attempt_number")));
        row.setShipmentId(shipment != null ? strOrNull(shipment.get("id")) : null);
        row.setPayload(rawBody.length() > MAX_STORED_PAYLOAD
                ? rawBody.substring(0, MAX_STORED_PAYLOAD) : rawBody);

        try {
            return deliveryRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the same key between the lookup and the insert.
            throw new DuplicateDeliveryException(key);
        }
    }

    /** Records how the delivery ended. Never throws — the carrier already got its answer. */
    private void finish(CargoWebhookDelivery delivery, String status, String error, Order order) {
        try {
            delivery.setStatus(status);
            delivery.setErrorMessage(error != null && error.length() > 500
                    ? error.substring(0, 500) : error);
            if (order != null) {
                delivery.setOrderId(order.getId());
                delivery.setOrderNumber(order.getOrderNumber());
            }
            deliveryRepository.save(delivery);
        } catch (Exception e) {
            log.warn("[KargonomiWebhook] delivery kaydı güncellenemedi: {}", e.toString());
        }
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

    /** Signals that this event has already been accounted for. */
    private static class DuplicateDeliveryException extends RuntimeException {
        final String key;
        DuplicateDeliveryException(String key) {
            super(null, null, false, false);
            this.key = key;
        }
    }

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

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hesaplanamadı: " + e.getMessage(), e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    /**
     * The event type lives at {@code meta.webhook.event_type} in Kargonomi's documented payload,
     * not at the top of {@code meta}. Reading only the top level left every stored delivery with a
     * blank type, which is the column an operator scans first when asking what a batch of
     * notifications was about. The top-level read stays as a fallback in case the shape changes.
     */
    @SuppressWarnings("unchecked")
    private static String eventTypeOf(Map<String, Object> meta) {
        Object nested = meta.get("webhook");
        if (nested instanceof Map<?, ?> webhook) {
            String type = strOrNull(((Map<String, Object>) webhook).get("event_type"));
            if (type != null) return type;
        }
        return strOrNull(meta.get("event_type"));
    }

    private static String strOrNull(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    private static Integer intOrNull(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
