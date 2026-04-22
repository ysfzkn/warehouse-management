package com.warehouse.controller;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.cargo.CargoApiProvider;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.KargonomiCargoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin Kargonomi entegrasyon yardımcı endpoint'leri:
 *   - GET  /api/admin/cargo/balance            → hesap bakiyesi
 *   - GET  /api/admin/cargo/orders/{id}/label  → kargo etiketi PDF
 *   - POST /api/admin/cargo/webhook/register   → Kargonomi'ye webhook kaydet
 */
@RestController
@RequestMapping("/api/admin/cargo")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCargoApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminCargoApiController.class);

    private final CargoApiService cargoApiService;
    private final OrderRepository orderRepository;
    private final AdminSecurityService adminSecurityService;

    public AdminCargoApiController(CargoApiService cargoApiService,
                                    OrderRepository orderRepository,
                                    AdminSecurityService adminSecurityService) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
        this.adminSecurityService = adminSecurityService;
    }

    /** Aktif sağlayıcının hesap bakiyesi (Kargonomi: {@code GET /user/credit}). */
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!cargoApiService.isEnabled()) {
            out.put("enabled", false);
            out.put("balance", null);
            return ResponseEntity.ok(out);
        }
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        out.put("enabled", true);
        out.put("provider", provider != null ? provider.getProviderName() : null);

        BigDecimal balance = cargoApiService.getProviderBalance();
        out.put("balance", balance);
        out.put("warning", balance != null && balance.compareTo(new BigDecimal("100")) < 0
                ? "Bakiye düşük — kargo gönderimi başarısız olabilir." : null);
        return ResponseEntity.ok(out);
    }

    /** Sipariş için kargo etiketini PDF olarak indirir. */
    @GetMapping("/orders/{orderId}/label")
    public ResponseEntity<?> downloadLabel(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Sipariş bulunamadı"));
        }
        if (order.getCargoProviderShipmentId() == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "message", "Bu sipariş için henüz kargo oluşturulmamış."));
        }
        byte[] pdf = cargoApiService.downloadShipmentLabel(order);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "Etiket indirilemedi. Kargo sağlayıcı yanıt vermiyor olabilir."));
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"kargo-etiket-" + order.getOrderNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Kargonomi'ye webhook kaydı — {@code shipment.updated} event'leri için.
     * Security code zorunlu (credential değişikliği sayılır).
     */
    @PostMapping("/webhook/register")
    public ResponseEntity<Map<String, Object>> registerWebhook(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);

        String callbackUrl = body.get("callbackUrl");
        String secret = body.get("secret");
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "callbackUrl zorunlu"));
        }

        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of(
                    "message", "Aktif sağlayıcı Kargonomi değil."));
        }

        boolean ok = k.registerWebhook(callbackUrl, secret);
        log.info("[Cargo] webhook register → url={}, ok={}", callbackUrl, ok);
        return ResponseEntity.ok(Map.of("success", ok, "callbackUrl", callbackUrl));
    }
}
