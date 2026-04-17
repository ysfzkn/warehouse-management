package com.warehouse.controller.store;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.OrderStatusHistory;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/orders")
public class StoreOrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderStatusHistoryRepository statusHistoryRepo;
    private final JwtService jwtService;
    private final com.warehouse.repository.CustomerRepository customerRepo;
    private final com.warehouse.repository.SupportTicketRepository supportTicketRepo;
    private final com.warehouse.repository.CargoProviderRepository cargoProviderRepo;
    private final com.warehouse.service.InvoiceService invoiceService;

    public StoreOrderController(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                                 OrderStatusHistoryRepository statusHistoryRepo, JwtService jwtService,
                                 com.warehouse.repository.CustomerRepository customerRepo,
                                 com.warehouse.repository.SupportTicketRepository supportTicketRepo,
                                 com.warehouse.repository.CargoProviderRepository cargoProviderRepo,
                                 com.warehouse.service.InvoiceService invoiceService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.jwtService = jwtService;
        this.customerRepo = customerRepo;
        this.supportTicketRepo = supportTicketRepo;
        this.cargoProviderRepo = cargoProviderRepo;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> myOrders(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.ok(new PagedResponse<>(java.util.List.of(), 0, size, 0, 0, true, true));

        Page<Order> orders = orderRepo.findByCustomerId(customerId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<Map<String, Object>> dtos = orders.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(new PagedResponse<>(dtos, orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages(), orders.isFirst(), orders.isLast()));
    }

    @GetMapping("/{orderNumber}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> orderDetail(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.notFound().build();

        return orderRepo.findByOrderNumber(orderNumber)
                .filter(o -> o.getCustomer() != null && o.getCustomer().getId().equals(customerId))
                .map(o -> {
                    Map<String, Object> dto = toDto(o);
                    dto.put("shippingAddress", o.getShippingAddressSnapshot());
                    dto.put("billingAddress", o.getBillingAddressSnapshot());
                    dto.put("customerNote", o.getCustomerNote());
                    dto.put("cargoCompany", o.getCargoCompany());
                    dto.put("cargoTrackingNo", o.getCargoTrackingNo());
                    // Items
                    List<Map<String, Object>> items = new ArrayList<>();
                    try {
                        for (OrderItem item : o.getItems()) {
                            Map<String, Object> it = new LinkedHashMap<>();
                            Map<String, Object> snap = item.getProductSnapshot();
                            it.put("productName", snap != null ? snap.getOrDefault("name", "—") : "—");
                            it.put("productSku", snap != null ? snap.getOrDefault("sku", "") : "");
                            it.put("quantity", item.getQuantity());
                            it.put("unitPrice", item.getUnitPrice());
                            it.put("lineTotal", item.getLineTotal());
                            // Product image
                            String imageUrl = null;
                            try {
                                if (item.getProduct() != null && item.getProduct().getImages() != null && !item.getProduct().getImages().isEmpty()) {
                                    var img = item.getProduct().getImages().stream().filter(i -> i.isPrimary()).findFirst().orElse(item.getProduct().getImages().get(0));
                                    imageUrl = "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true";
                                }
                            } catch (Exception ignored2) {}
                            it.put("imageUrl", imageUrl);
                            it.put("productSlug", item.getProduct() != null ? item.getProduct().getSlug() : null);
                            items.add(it);
                        }
                    } catch (Exception ignored) {}
                    dto.put("items", items);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderNumber}/support-tickets")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createSupportTicket(HttpServletRequest request, @PathVariable String orderNumber,
                                                  @RequestBody Map<String, String> body) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));

        String topic = body.getOrDefault("topic", "").trim();
        String message = body.getOrDefault("message", "").trim();
        if (topic.isEmpty() || message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Konu ve mesaj alanları zorunludur."));
        }

        var customer = customerRepo.findById(customerId);
        if (customer.isEmpty()) return ResponseEntity.notFound().build();

        com.warehouse.entity.SupportTicket ticket = com.warehouse.entity.SupportTicket.builder()
            .customer(customer.get())
            .orderNumber(orderNumber)
            .topic(topic)
            .message(message)
            .status("OPEN")
            .build();
        supportTicketRepo.save(ticket);

        return ResponseEntity.ok(Map.of("message", "Destek talebiniz alındı. En kısa sürede size dönüş yapacağız."));
    }

    @PostMapping("/{orderNumber}/return-requests")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> requestReturn(HttpServletRequest request, @PathVariable String orderNumber,
                                            @RequestBody Map<String, String> body) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));

        Optional<Order> orderOpt = orderRepo.findByOrderNumber(orderNumber);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();
        Order order = orderOpt.get();

        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
            return ResponseEntity.notFound().build();
        }

        // Only SHIPPED or DELIVERED orders can be returned
        if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.DELIVERED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bu sipariş için iade talebi oluşturulamaz. Sadece kargoda veya teslim edilmiş siparişler iade edilebilir."));
        }

        // Check not already in return process
        if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bu sipariş için zaten bir iade talebi mevcut."));
        }

        String reason = body.getOrDefault("reason", "");
        String note = body.getOrDefault("note", "");

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepo.save(order);

        statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                order, oldStatus, OrderStatus.RETURN_REQUESTED,
                "customer", "CUSTOMER", "İade talebi: " + reason + (note.isEmpty() ? "" : " — " + note)));

        return ResponseEntity.ok(Map.of("message", "İade talebiniz alındı. En kısa sürede değerlendirilecektir."));
    }

    @GetMapping("/{orderNumber}/invoice")
    public ResponseEntity<?> downloadInvoice(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("message", "Giriş yapmanız gerekiyor."));

        return orderRepo.findByOrderNumber(orderNumber)
            .filter(o -> o.getCustomer() != null && o.getCustomer().getId().equals(customerId))
            .map(order -> {
                if (order.getInvoiceUrl() == null || order.getInvoiceUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Bu sipariş için henüz fatura yüklenmemiş."));
                }
                try {
                    java.nio.file.Path filePath = java.nio.file.Paths.get(order.getInvoiceUrl());
                    if (!java.nio.file.Files.exists(filePath)) {
                        return ResponseEntity.notFound().build();
                    }
                    org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
                    String contentType = java.nio.file.Files.probeContentType(filePath);
                    if (contentType == null) contentType = "application/octet-stream";
                    String ext = order.getInvoiceUrl().contains(".") ? order.getInvoiceUrl().substring(order.getInvoiceUrl().lastIndexOf('.')) : "";
                    return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=fatura-" + order.getOrderNumber() + ext)
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(resource);
                } catch (Exception e) {
                    return ResponseEntity.internalServerError().body(Map.of("message", "Fatura indirilemedi."));
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(Order o) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", o.getId());
        dto.put("orderNumber", o.getOrderNumber());
        dto.put("status", o.getStatus() != null ? o.getStatus().name() : null);
        dto.put("grandTotal", o.getGrandTotal());
        dto.put("subtotal", o.getSubtotal());
        dto.put("shippingCost", o.getShippingCost());
        dto.put("discountAmount", o.getDiscountAmount());
        dto.put("paymentMethod", o.getPaymentMethod());
        int itemCount = 0;
        try { if (o.getItems() != null) itemCount = o.getItems().size(); } catch (Exception ignored) {}
        dto.put("itemCount", itemCount);
        dto.put("cargoTrackingNo", o.getCargoTrackingNo());
        dto.put("cargoCompany", o.getCargoCompany() != null ? o.getCargoCompany().name() : null);
        dto.put("cargoProviderName", o.getCargoProviderName());
        // Build tracking URL
        String trackingUrl = null;
        if (o.getCargoTrackingNo() != null && !o.getCargoTrackingNo().isBlank()) {
            try {
                // First try CargoProvider table
                if (o.getCargoProviderId() != null) {
                    cargoProviderRepo.findById(o.getCargoProviderId()).ifPresent(cp -> {
                        if (cp.getTrackingUrlTemplate() != null) {
                            dto.put("cargoTrackingUrl", cp.getTrackingUrlTemplate().replace("{trackingNo}", o.getCargoTrackingNo()));
                        }
                    });
                }
                // Fallback: match by cargo company code
                if (!dto.containsKey("cargoTrackingUrl") && o.getCargoCompany() != null) {
                    cargoProviderRepo.findByCode(o.getCargoCompany().name()).ifPresent(cp -> {
                        if (cp.getTrackingUrlTemplate() != null) {
                            dto.put("cargoTrackingUrl", cp.getTrackingUrlTemplate().replace("{trackingNo}", o.getCargoTrackingNo()));
                        }
                    });
                }
            } catch (Exception ignored) {}
        }
        dto.put("invoiceUrl", o.getInvoiceUrl());
        dto.put("invoiceNumber", o.getInvoiceNumber());
        dto.put("createdAt", o.getCreatedAt());
        return dto;
    }

    /**
     * Müşterinin kendi siparişi için e-fatura PDF'ini indirir.
     */
    @GetMapping("/{orderNumber}/invoice/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadInvoicePdf(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("error", "Giriş yapmanız gerekiyor."));

        Order order = orderRepo.findByOrderNumber(orderNumber).orElse(null);
        if (order == null || !order.getCustomer().getId().equals(customerId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Sipariş bulunamadı."));
        }

        var invoiceOpt = invoiceService.getInvoiceByOrderId(order.getId());
        if (invoiceOpt.isEmpty() || !invoiceOpt.get().isHasPdf()) {
            return ResponseEntity.status(404).body(Map.of("error", "Bu sipariş için fatura henüz oluşturulmamış."));
        }

        try {
            byte[] pdf = invoiceService.downloadInvoicePdf(invoiceOpt.get().getId());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"fatura-" + orderNumber + ".pdf\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Fatura indirilemedi."));
        }
    }

    /**
     * Müşterinin siparişi için fatura bilgilerini getirir (e-Fatura sistem bilgisi).
     */
    @GetMapping("/{orderNumber}/invoice/info")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getInvoiceInfo(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("error", "Giriş yapmanız gerekiyor."));

        Order order = orderRepo.findByOrderNumber(orderNumber).orElse(null);
        if (order == null || !order.getCustomer().getId().equals(customerId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Sipariş bulunamadı."));
        }

        var invoiceOpt = invoiceService.getInvoiceByOrderId(order.getId());
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasInvoice", false));
        }

        var inv = invoiceOpt.get();
        return ResponseEntity.ok(Map.of(
                "hasInvoice", true,
                "invoiceNumber", inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "",
                "status", inv.getStatus().name(),
                "invoiceType", inv.getInvoiceType().name(),
                "hasPdf", inv.isHasPdf(),
                "issuedAt", inv.getIssuedAt() != null ? inv.getIssuedAt().toString() : ""
        ));
    }

    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else {
                // Try cookie
                if (request.getCookies() != null) {
                    for (var c : request.getCookies()) {
                        if ("access_token".equals(c.getName())) { token = c.getValue(); break; }
                    }
                }
            }
            if (token == null) return null;
            return jwtService.extractCustomerId(token);
        } catch (Exception e) { return null; }
    }
}
