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
 * Admin Kargonomi integration helper endpoints:
 *   - GET  /api/admin/cargo/balance            → account balance
 *   - GET  /api/admin/cargo/orders/{id}/label  → shipment label PDF
 *   - POST /api/admin/cargo/webhook/register   → register a webhook with Kargonomi
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

    /** Active provider's account balance (Kargonomi: {@code GET /user/credit}). */
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

    /** Downloads the shipment label for an order as a PDF. */
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
     * Register a webhook with Kargonomi — for {@code shipment.updated} events.
     * Security code required (counts as a credential change).
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

    /** List all registered webhooks. */
    @GetMapping("/webhooks")
    public ResponseEntity<?> listWebhooks() {
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        return ResponseEntity.ok(Map.of("items", k.listWebhooks()));
    }

    /** Webhook details (by id). */
    @GetMapping("/webhooks/{id}")
    public ResponseEntity<?> getWebhook(@PathVariable long id) {
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        Map<String, Object> data = k.getWebhook(id);
        return data != null ? ResponseEntity.ok(data) : ResponseEntity.notFound().build();
    }

    /** Update a webhook (url or is_active). */
    @org.springframework.web.bind.annotation.PutMapping("/webhooks/{id}")
    public ResponseEntity<?> updateWebhook(@PathVariable long id, @RequestBody Map<String, Object> body,
                                            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        String url = (String) body.get("url");
        Boolean isActive = (Boolean) body.get("is_active");
        boolean ok = k.updateWebhook(id, url, isActive);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    /** Delete a webhook. */
    @org.springframework.web.bind.annotation.DeleteMapping("/webhooks/{id}")
    public ResponseEntity<?> deleteWebhook(@PathVariable long id,
                                            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        boolean ok = k.deleteWebhook(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    /**
     * Reconciliation: list the most recent shipments on the Kargonomi side.
     * Useful for comparing against the ones in our DB.
     */
    @GetMapping("/shipments")
    public ResponseEntity<?> listShipments(@RequestParam(defaultValue = "1") int page) {
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        return ResponseEntity.ok(k.listShipments(page));
    }

    /**
     * Registers a warehouse (sender address) with Kargonomi during initial setup.
     * The returned warehouse_id is written to site_settings as
     * {@code kargonomi_warehouse_id} (the admin can also enter it manually).
     */
    @PostMapping("/warehouses")
    public ResponseEntity<?> registerWarehouse(
            @RequestBody KargonomiCargoProvider.WarehouseRegistrationRequest req,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        CargoApiProvider provider = cargoApiService.getActiveProvider();
        if (!(provider instanceof KargonomiCargoProvider k)) {
            return ResponseEntity.status(400).body(Map.of("message", "Aktif sağlayıcı Kargonomi değil."));
        }
        Long warehouseId = k.registerWarehouse(req);
        if (warehouseId == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Depo kaydı başarısız. İl/ilçe lookup veya API hatası olabilir."));
        }
        log.info("[Cargo] Kargonomi warehouse registered: id={}, name={}", warehouseId, req.getName());
        return ResponseEntity.ok(Map.of("warehouseId", warehouseId,
                "message", "Depo Kargonomi'ye kaydedildi. Bu ID'yi 'kargonomi_warehouse_id' ayarına yazın."));
    }
}
