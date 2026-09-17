package com.warehouse.controller;

import com.warehouse.entity.CargoShipmentEvent;
import com.warehouse.entity.CargoShipmentOutbox;
import com.warehouse.entity.Order;
import com.warehouse.repository.CargoShipmentEventRepository;
import com.warehouse.repository.CargoShipmentOutboxRepository;
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
import java.util.List;
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
    private final CargoShipmentEventRepository eventRepository;
    private final CargoShipmentOutboxRepository outboxRepository;
    private final com.warehouse.service.cargo.CargoLabelBatchService labelBatchService;
    private final com.warehouse.service.cargo.CargoPerformanceService performanceService;

    public AdminCargoApiController(CargoApiService cargoApiService,
                                    OrderRepository orderRepository,
                                    AdminSecurityService adminSecurityService,
                                    CargoShipmentEventRepository eventRepository,
                                    CargoShipmentOutboxRepository outboxRepository,
                                    com.warehouse.service.cargo.CargoLabelBatchService labelBatchService,
                                    com.warehouse.service.cargo.CargoPerformanceService performanceService) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
        this.adminSecurityService = adminSecurityService;
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.labelBatchService = labelBatchService;
        this.performanceService = performanceService;
    }

    /**
     * Carrier scoreboard: volume, delivery rate, problem rate and average days in transit.
     * Feeds the decision of which carrier to default to — and the next contract negotiation.
     */
    @GetMapping("/performance")
    public ResponseEntity<?> performance(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(Map.of(
                "days", days,
                "items", performanceService.report(days)));
    }

    /**
     * Every selected order's label in one PDF, ready for the printer.
     *
     * <p>Orders without a shipment are reported in the {@code X-Skipped-Orders} header rather
     * than silently missing from the stack — the header is readable from the browser even on a
     * blob download, which a JSON body would not be.
     */
    @PostMapping("/labels")
    public ResponseEntity<?> batchLabels(@RequestBody Map<String, List<Long>> body) {
        List<Long> orderIds = body.get("orderIds");
        if (orderIds == null || orderIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "En az bir sipariş seçin."));
        }

        var result = labelBatchService.buildMergedLabels(orderIds);
        if (result.isEmpty()) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "Hiçbir etiket indirilemedi.",
                    "skipped", result.skipped()));
        }

        String skippedHeader = result.skipped().isEmpty() ? "" : String.join(", ",
                result.skipped().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList());

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + labelBatchService.suggestedFileName() + "\"")
                .header("X-Included-Count", String.valueOf(result.includedOrders().size()))
                .header("X-Skipped-Orders", skippedHeader)
                .header("Access-Control-Expose-Headers", "X-Included-Count, X-Skipped-Orders")
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.pdf());
    }

    /**
     * Corrects the recipient on an order already handed to the carrier — a wrong phone number or
     * a missing apartment number, without cancelling and recreating the shipment.
     */
    @PatchMapping("/orders/{orderId}/recipient")
    public ResponseEntity<?> correctRecipient(@PathVariable Long orderId,
                                               @RequestBody Map<String, String> body) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Sipariş bulunamadı"));
        }
        boolean ok = cargoApiService.correctRecipient(order,
                body.get("recipientName"), body.get("recipientPhone"), body.get("recipientAddress"),
                body.get("city"), body.get("district"));

        return ok
                ? ResponseEntity.ok(Map.of("success", true, "message", "Alıcı bilgisi güncellendi."))
                : ResponseEntity.status(502).body(Map.of("success", false,
                        "message", "Sipariş güncellendi ama kargo firması değişikliği kabul etmedi. "
                                + "Kargo çıkmış olabilir — kargo firması panelinden kontrol edin."));
    }

    /**
     * Withdraws the shipment: deletes it if it is still a draft, cancels it otherwise.
     * Runs automatically when an order is cancelled; this endpoint is for doing it by hand.
     */
    @PostMapping("/orders/{orderId}/withdraw")
    public ResponseEntity<?> withdrawShipment(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Sipariş bulunamadı"));
        }
        if (order.getCargoProviderShipmentId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bu siparişin kargo gönderisi yok."));
        }
        boolean ok = cargoApiService.withdrawShipment(order);
        return ok
                ? ResponseEntity.ok(Map.of("success", true, "message", "Kargo gönderisi geri çekildi."))
                : ResponseEntity.status(502).body(Map.of("success", false,
                        "message", "Kargo geri çekilemedi — 36 saatlik iptal süresi geçmiş olabilir."));
    }

    /**
     * The order's cargo history — every status change and carrier movement we were told about,
     * newest first. Survives the carrier overwriting its own current status.
     */
    @GetMapping("/orders/{orderId}/events")
    public ResponseEntity<?> orderCargoEvents(@PathVariable Long orderId) {
        List<CargoShipmentEvent> events = eventRepository.findByOrderIdOrderByOccurredAtDescIdDesc(orderId);
        List<Map<String, Object>> items = events.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("statusCode", e.getStatusCode());
            row.put("label", e.getStatusLabel() != null && !e.getStatusLabel().isBlank()
                    ? e.getStatusLabel() : e.getDescription());
            row.put("description", e.getDescription());
            row.put("location", e.getLocation());
            row.put("occurredAt", e.getOccurredAt());
            row.put("recordedAt", e.getCreatedAt());
            row.put("source", e.getSource());
            return row;
        }).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    /**
     * Shipments still owed. PENDING entries are waiting for their next retry; ABANDONED ones
     * ran out of attempts and need creating by hand in the Kargonomi panel.
     */
    @GetMapping("/outbox")
    public ResponseEntity<?> outbox() {
        List<Map<String, Object>> items = outboxRepository.findAll().stream()
                .filter(o -> !CargoShipmentOutbox.STATUS_SUCCEEDED.equals(o.getStatus()))
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderId", o.getOrderId());
                    row.put("orderNumber", o.getOrderNumber());
                    row.put("status", o.getStatus());
                    row.put("attempts", o.getAttempts());
                    row.put("nextAttemptAt", o.getNextAttemptAt());
                    row.put("lastError", o.getLastError());
                    return row;
                }).toList();
        return ResponseEntity.ok(Map.of("items", items,
                "pending", outboxRepository.countByStatus(CargoShipmentOutbox.STATUS_PENDING),
                "abandoned", outboxRepository.countByStatus(CargoShipmentOutbox.STATUS_ABANDONED)));
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

        com.warehouse.service.cargo.CargoBalance balance = cargoApiService.getProviderBalance();
        out.put("balance", balance.amount());
        out.put("balanceState", balance.state().name());
        out.put("balanceText", balance.describe());

        // A balance we could not read is not the same as a healthy one — say which it is,
        // rather than leaving the screen blank and reassuring.
        String warning = null;
        if (balance.isDepleted()) {
            warning = balance.describe();
        } else if (balance.state() == com.warehouse.service.cargo.CargoBalance.State.OK
                && balance.amount() != null
                && balance.amount().compareTo(new BigDecimal("100")) < 0) {
            warning = "Bakiye düşük — kargo gönderimi başarısız olabilir.";
        } else if (balance.state() == com.warehouse.service.cargo.CargoBalance.State.UNREACHABLE) {
            warning = balance.describe();
        }
        out.put("warning", warning);
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
