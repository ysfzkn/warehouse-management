package com.warehouse.controller.store;

import com.warehouse.security.UploadValidator;
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
    private final com.warehouse.service.ReturnRequestService returnService;

    public StoreOrderController(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                                 OrderStatusHistoryRepository statusHistoryRepo, JwtService jwtService,
                                 com.warehouse.repository.CustomerRepository customerRepo,
                                 com.warehouse.repository.SupportTicketRepository supportTicketRepo,
                                 com.warehouse.repository.CargoProviderRepository cargoProviderRepo,
                                 com.warehouse.service.InvoiceService invoiceService,
                                 com.warehouse.service.ReturnRequestService returnService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.jwtService = jwtService;
        this.customerRepo = customerRepo;
        this.supportTicketRepo = supportTicketRepo;
        this.cargoProviderRepo = cargoProviderRepo;
        this.invoiceService = invoiceService;
        this.returnService = returnService;
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
                                if (item.getProduct() != null) {
                                    var img = com.warehouse.util.ProductImageUtil.displayCover(item.getProduct().getImages()).orElse(null);
                                    if (img != null) {
                                        imageUrl = "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true";
                                    }
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

    /**
     * Create a return request. Body: {reason, note, items?:[{orderItemId, quantity, reason?}]}.
     * When {@code items} is omitted the whole order is returned. Persists a proper
     * ReturnRequest (the previous version only flipped the order status), restores
     * nothing yet, notifies the admin and emails the customer — all in the service.
     */
    @PostMapping("/{orderNumber}/return-requests")
    public ResponseEntity<?> requestReturn(HttpServletRequest request, @PathVariable String orderNumber,
                                            @RequestBody Map<String, Object> body) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));

        com.warehouse.enums.ReturnReason reason = parseReason(body.get("reason"));
        String note = body.get("note") != null ? body.get("note").toString() : null;
        List<com.warehouse.service.ReturnRequestService.ReturnItemRequest> items = parseItems(body.get("items"));

        var rr = returnService.createReturn(customerId, orderNumber, reason, note, items);
        return ResponseEntity.ok(Map.of(
                "message", "İade talebiniz alındı. En kısa sürede değerlendirilecektir.",
                "returnNumber", rr.getReturnNumber()));
    }

    /** Customer's own return requests (tracking list). */
    @GetMapping("/return-requests")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> myReturns(HttpServletRequest request,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.ok(new PagedResponse<>(java.util.List.of(), 0, size, 0, 0, true, true));
        var result = returnService.listForCustomer(customerId, PageRequest.of(page, size));
        var content = result.getContent().stream()
                .map(com.warehouse.dto.store.ReturnDtoMapper::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    /** Single return-request detail/tracking for the owning customer. */
    @GetMapping("/return-requests/{returnNumber}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> myReturnDetail(HttpServletRequest request, @PathVariable String returnNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("message", "Giriş yapmanız gerekiyor."));
        return ResponseEntity.ok(com.warehouse.dto.store.ReturnDtoMapper.toMap(
                returnService.getForCustomer(customerId, returnNumber)));
    }

    /** Customer attaches an evidence photo to their own return request. */
    @PostMapping("/return-requests/{returnNumber}/photos")
    public ResponseEntity<?> uploadReturnPhoto(HttpServletRequest request, @PathVariable String returnNumber,
                                               @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("message", "Giriş yapmanız gerekiyor."));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Dosya boş."));
        UploadValidator.ImageType imageType;
        try {
            imageType = UploadValidator.validateImage(file, 12L * 1024 * 1024);
        } catch (UploadValidator.InvalidUploadException e) {
            return ResponseEntity.status(415).body(Map.of("message", e.getMessage()));
        }
        try {
            Long id = returnService.addPhoto(customerId, returnNumber,
                    "upload." + imageType.extension, imageType.contentType, file.getInputStream());
            return ResponseEntity.ok(Map.of("id", id, "url",
                    "/api/admin/returns/photos/" + id + "/view"
                            + com.warehouse.security.SignedUrlService.query("return-photo", id)));
        } catch (java.io.IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Fotoğraf yüklenemedi."));
        }
    }

    private com.warehouse.enums.ReturnReason parseReason(Object raw) {
        if (raw == null) return com.warehouse.enums.ReturnReason.OTHER;
        try {
            return com.warehouse.enums.ReturnReason.valueOf(raw.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.warehouse.enums.ReturnReason.OTHER;
        }
    }

    @SuppressWarnings("unchecked")
    private List<com.warehouse.service.ReturnRequestService.ReturnItemRequest> parseItems(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return java.util.List.of();
        List<com.warehouse.service.ReturnRequestService.ReturnItemRequest> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object oid = m.get("orderItemId");
                Object qty = m.get("quantity");
                Object rsn = m.get("reason");
                if (oid != null) {
                    out.add(new com.warehouse.service.ReturnRequestService.ReturnItemRequest(
                            Long.valueOf(oid.toString()),
                            qty != null ? Integer.parseInt(qty.toString()) : 1,
                            rsn != null ? rsn.toString() : null));
                }
            }
        }
        return out;
    }

    /**
     * Endpoint for the customer to download their order invoice.
     *
     * <p>Flow (in priority order):
     * <ol>
     *   <li><b>Modern:</b> the PDF generated by Logo via {@code InvoiceService}
     *       (Caffeine-cached, KVKK-compliant/secure — customer-id match is checked).</li>
     *   <li><b>Legacy:</b> old-system fallback if there is a file path in the
     *       {@code Order.invoiceUrl} field.</li>
     * </ol></p>
     */
    @GetMapping("/{orderNumber}/invoice")
    public ResponseEntity<?> downloadInvoice(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("message", "Giriş yapmanız gerekiyor."));

        return orderRepo.findByOrderNumber(orderNumber)
            .filter(o -> o.getCustomer() != null && o.getCustomer().getId().equals(customerId))
            .map(order -> {
                // 1) Modern: Logo PDF via InvoiceService (cached)
                try {
                    var invoiceDto = invoiceService.getInvoiceByOrderId(order.getId());
                    if (invoiceDto.isPresent()
                            && invoiceDto.get().getStatus() == com.warehouse.enums.InvoiceStatus.APPROVED) {
                        byte[] pdf = invoiceService.downloadInvoicePdf(invoiceDto.get().getId());
                        if (pdf != null && pdf.length > 0) {
                            return ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=fatura-" + order.getOrderNumber() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body((Object) pdf);
                        }
                    }
                } catch (Exception e) {
                    // If the InvoiceService path didn't work, fall back to legacy
                }

                // 2) Legacy: if Order.invoiceUrl exists, serve from the file system
                if (order.getInvoiceUrl() == null || order.getInvoiceUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "Bu sipariş için henüz fatura hazır değil. " +
                                       "Genelde sipariş ödendikten sonra 5-10 dakika içinde hazırlanır."));
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
        // Tracking URL priority order:
        //   1. cargo_providers.trackingUrlTemplate (admin-configured; most reliable)
        //   2. kargonomi_slug → KargonomiCargoProvider.buildCarrierTrackingUrl generic mapping
        //   3. null
        if (o.getCargoTrackingNo() != null && !o.getCargoTrackingNo().isBlank()) {
            try {
                String trackingNo = o.getCargoTrackingNo();
                String[] urlHolder = { null };

                // 1) From the DB by CargoProvider.id
                if (o.getCargoProviderId() != null) {
                    cargoProviderRepo.findById(o.getCargoProviderId()).ifPresent(cp -> {
                        if (cp.getTrackingUrlTemplate() != null && !cp.getTrackingUrlTemplate().isBlank()) {
                            urlHolder[0] = cp.getTrackingUrlTemplate().replace("{trackingNo}", trackingNo);
                        }
                    });
                }
                // 2) By code
                if (urlHolder[0] == null && o.getCargoCompany() != null) {
                    cargoProviderRepo.findByCode(o.getCargoCompany().name()).ifPresent(cp -> {
                        if (cp.getTrackingUrlTemplate() != null && !cp.getTrackingUrlTemplate().isBlank()) {
                            urlHolder[0] = cp.getTrackingUrlTemplate().replace("{trackingNo}", trackingNo);
                        }
                    });
                }
                // 3) Generic fallback — public tracking URL based on the carrier slug
                if (urlHolder[0] == null && o.getCargoCompany() != null) {
                    String slug = o.getCargoCompany().name().toLowerCase();
                    urlHolder[0] = genericCarrierTrackingUrl(slug, trackingNo);
                }

                if (urlHolder[0] != null) dto.put("cargoTrackingUrl", urlHolder[0]);
            } catch (Exception ignored) {}
        }
        dto.put("invoiceUrl", o.getInvoiceUrl());
        dto.put("invoiceNumber", o.getInvoiceNumber());
        dto.put("createdAt", o.getCreatedAt());
        return dto;
    }

    /**
     * Fallback used when cargo_providers.trackingUrlTemplate is not defined.
     * Public tracking URL patterns for common carriers in Turkey.
     */
    private static String genericCarrierTrackingUrl(String slug, String trackingNo) {
        if (slug == null || trackingNo == null) return null;
        return switch (slug.toLowerCase()) {
            case "yurtici" -> "https://selfservis.yurticikargo.com/takip?code=" + trackingNo;
            case "aras"    -> "https://kargotakip.araskargo.com.tr/mainpage.aspx?code=" + trackingNo;
            case "mng"     -> "https://kargotakip.mngkargo.com.tr/?takipNo=" + trackingNo;
            case "ptt"     -> "https://gonderitakip.ptt.gov.tr/Track/Verify?q=" + trackingNo;
            case "surat"   -> "https://www.suratkargo.com.tr/KargoTakip/?kargotakipno=" + trackingNo;
            case "ups"     -> "https://www.ups.com/track?tracknum=" + trackingNo;
            case "sendeo"  -> "https://sendeo.com.tr/gonderi-takibi?tno=" + trackingNo;
            default -> null;
        };
    }

    /**
     * Downloads the e-invoice PDF for the customer's own order.
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
     * Retrieves the invoice information for the customer's order (e-invoice system info).
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
