package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.admin.AdminOrderDto;
import com.warehouse.dto.admin.AdminOrderDetailDto;
import com.warehouse.dto.admin.OrderStatusUpdateRequest;
import com.warehouse.dto.admin.OrderCargoUpdateRequest;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.OrderStatusHistory;
import com.warehouse.enums.CargoCompany;
import com.warehouse.enums.OrderStatus;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.service.EmailService;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventType;
import com.warehouse.enums.StockEventSource;
import com.warehouse.repository.StockEventRepository;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.PaymentService;
import com.warehouse.util.CurrentUser;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PaymentService paymentService;
    private final AdminSecurityService adminSecurityService;
    private final com.warehouse.repository.PaymentTransactionRepository paymentRepo;
    private final com.warehouse.repository.StockRepository stockRepository;
    private final StockEventRepository stockEventRepository;
    private final EmailService emailService;
    private final com.warehouse.service.notification.NotificationDispatchService notificationDispatchService;
    private final com.warehouse.service.cargo.CargoApiService cargoApiService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.warehouse.service.PhotoStorageService photoStorageService;

    public AdminOrderController(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderStatusHistoryRepository statusHistoryRepository,
                                 PaymentService paymentService,
                                 AdminSecurityService adminSecurityService,
                                 com.warehouse.repository.PaymentTransactionRepository paymentRepo,
                                 com.warehouse.repository.StockRepository stockRepository,
                                 StockEventRepository stockEventRepository,
                                 EmailService emailService,
                                 com.warehouse.service.notification.NotificationDispatchService notificationDispatchService,
                                 com.warehouse.service.cargo.CargoApiService cargoApiService,
                                 org.springframework.context.ApplicationEventPublisher eventPublisher,
                                 com.warehouse.service.PhotoStorageService photoStorageService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.paymentService = paymentService;
        this.adminSecurityService = adminSecurityService;
        this.paymentRepo = paymentRepo;
        this.stockRepository = stockRepository;
        this.stockEventRepository = stockEventRepository;
        this.emailService = emailService;
        this.notificationDispatchService = notificationDispatchService;
        this.cargoApiService = cargoApiService;
        this.eventPublisher = eventPublisher;
        this.photoStorageService = photoStorageService;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<AdminOrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String cargoCompany,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        // Parse filters
        String statusFilter = (status != null && !status.isBlank()) ? status : null;
        String pmFilter = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : null;
        String cargoFilter = (cargoCompany != null && !cargoCompany.isBlank()) ? cargoCompany : null;
        java.time.LocalDateTime startDt = null, endDt = null;
        try { if (startDate != null && !startDate.isBlank()) startDt = java.time.LocalDate.parse(startDate).atStartOfDay(); } catch (Exception ignored) {}
        try { if (endDate != null && !endDate.isBlank()) endDt = java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay(); } catch (Exception ignored) {}
        String searchParam = (search != null && !search.isBlank()) ? search : null;

        Page<Order> result = orderRepository.findAll(
            com.warehouse.repository.OrderSpecifications.withFilters(statusFilter, pmFilter, cargoFilter, startDt, endDt, searchParam),
            PageRequest.of(page, size, sort));

        List<AdminOrderDto> dtos = result.getContent().stream().map(o -> AdminOrderDto.builder()
            .id(o.getId())
            .orderNumber(o.getOrderNumber())
            .customerName(safeCustomerName(o))
            .customerEmail(safeCustomerEmail(o))
            .status(o.getStatus())
            .grandTotal(o.getGrandTotal())
            .paymentMethod(o.getPaymentMethod())
            .cargoCompany(o.getCargoCompany() != null ? o.getCargoCompany().name() : null)
            .cargoTrackingNo(o.getCargoTrackingNo())
            .itemCount(safeItemCount(o))
            .createdAt(o.getCreatedAt())
            .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(dtos, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<AdminOrderDetailDto> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithCustomer(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(id);

        return ResponseEntity.ok(AdminOrderDetailDto.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .customerName(safeCustomerName(order))
            .customerEmail(safeCustomerEmail(order))
            .customerPhone(safeCustomerPhone(order))
            .shippingAddress(order.getShippingAddressSnapshot())
            .billingAddress(order.getBillingAddressSnapshot())
            .subtotal(order.getSubtotal())
            .shippingCost(order.getShippingCost())
            .discountAmount(order.getDiscountAmount())
            .vatTotal(order.getVatTotal())
            .grandTotal(order.getGrandTotal())
            .couponCode(order.getCouponCode())
            .paymentMethod(order.getPaymentMethod())
            .installmentCount(order.getInstallmentCount())
            .cargoCompany(order.getCargoCompany() != null ? order.getCargoCompany().name() : null)
            .cargoTrackingNo(order.getCargoTrackingNo())
            .estimatedDeliveryDate(order.getEstimatedDeliveryDate())
            .customerNote(order.getCustomerNote())
            .adminNote(order.getAdminNote())
            .ipAddress(order.getIpAddress())
            .invoiceNumber(order.getInvoiceNumber())
            .invoiceUrl(order.getInvoiceUrl())
            .items(items.stream().map(i -> {
                String warehouseName = "";
                try {
                    if (i.getWarehouseId() != null) {
                        var wh = stockRepository.findById(i.getStockId());
                        if (wh.isPresent()) warehouseName = wh.get().getWarehouse().getName();
                    }
                } catch (Exception ignored) {}
                Long productId = null;
                String imageUrl = null;
                try {
                    if (i.getProduct() != null) {
                        productId = i.getProduct().getId();
                        if (i.getProduct().getImages() != null && !i.getProduct().getImages().isEmpty()) {
                            var img = i.getProduct().getImages().stream().filter(im -> im.isPrimary()).findFirst().orElse(i.getProduct().getImages().get(0));
                            imageUrl = "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true";
                        }
                    }
                } catch (Exception ignored2) {}
                return AdminOrderDetailDto.OrderItemDto.builder()
                    .id(i.getId())
                    .productId(productId)
                    .productName(i.getProductSnapshot() != null ? (String) i.getProductSnapshot().get("name") : "")
                    .productSku(i.getProductSnapshot() != null ? (String) i.getProductSnapshot().get("sku") : "")
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .lineTotal(i.getLineTotal())
                    .warehouseId(i.getWarehouseId())
                    .warehouseName(warehouseName)
                    .stockId(i.getStockId())
                    .imageUrl(imageUrl)
                    .build();
            }).collect(Collectors.toList()))
            .statusHistory(history.stream().map(h -> AdminOrderDetailDto.StatusHistoryDto.builder()
                .oldStatus(h.getOldStatus())
                .newStatus(h.getNewStatus())
                .changedBy(h.getChangedBy())
                .note(h.getNote())
                .createdAt(h.getCreatedAt())
                .build()).collect(Collectors.toList()))
            .createdAt(order.getCreatedAt())
            .build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> updateStatus(@PathVariable Long id,
                                                             @Valid @RequestBody OrderStatusUpdateRequest body) {
        Order order = orderRepository.findByIdWithCustomer(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus = body.getStatus();

        // Validate transition
        if (!com.warehouse.util.OrderStatusMachine.isValidTransition(oldStatus, newStatus)) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    com.warehouse.util.OrderStatusMachine.getLabel(oldStatus) + " durumundan "
                    + com.warehouse.util.OrderStatusMachine.getLabel(newStatus) + " durumuna geçiş yapılamaz.");
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistoryFactory.create(
            order, oldStatus, newStatus,
            CurrentUser.usernameOrSystem(), "ADMIN", body.getNote()));

        // RETURNED/REFUNDED'a geçişte fatura iptal/credit note event'i fırlat
        // (InvoiceCancellationListener AFTER_COMMIT + @Async olarak yakalar)
        if (newStatus == OrderStatus.RETURNED || newStatus == OrderStatus.REFUNDED) {
            eventPublisher.publishEvent(new com.warehouse.event.OrderReturnedEvent(
                    this, order.getId(), order.getOrderNumber(),
                    order.getGrandTotal(), body.getNote(), CurrentUser.usernameOrSystem()));
        }

        // If SHIPPED and no tracking number yet → auto-create shipment via cargo API
        if (newStatus == OrderStatus.SHIPPED
                && (order.getCargoTrackingNo() == null || order.getCargoTrackingNo().isBlank())
                && cargoApiService.isEnabled()) {
            try {
                com.warehouse.service.cargo.CargoShipmentResult cargoResult =
                        cargoApiService.createShipmentForOrder(order);
                if (cargoResult != null && !cargoResult.isSuccess()) {
                    org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("Kargo gönderi oluşturulamadı (sipariş {}): {}",
                              order.getOrderNumber(), cargoResult.getErrorMessage());
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("Kargo API exception (sipariş {}): {}", order.getOrderNumber(), e.getMessage());
            }
        }

        // If DELIVERED → deduct reserved stock (convert reservation to actual sale) + log StockEvent
        if (newStatus == OrderStatus.DELIVERED) {
            try {
                var items = orderItemRepository.findByOrderId(order.getId());
                for (var item : items) {
                    Long productId = null;
                    try { productId = item.getProduct() != null ? item.getProduct().getId() : null; } catch (Exception ignored) {}

                    if (item.getStockId() != null) {
                        stockRepository.findById(item.getStockId()).ifPresent(stock -> {
                            int qty = item.getQuantity();
                            int oldQty = stock.getQuantity();
                            stock.setQuantity(Math.max(0, oldQty - qty));
                            stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - qty));
                            stockRepository.save(stock);

                            StockEvent event = new StockEvent();
                            event.setStockId(stock.getId());
                            event.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
                            event.setEventType(StockEventType.QUANTITY_CHANGED);
                            event.setOldValue(oldQty);
                            event.setNewValue(stock.getQuantity());
                            event.setSource(StockEventSource.ORDER);
                            event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " teslim edildi (" + qty + " adet)");
                            event.setOrderNumber(order.getOrderNumber());
                            stockEventRepository.save(event);
                        });
                    } else {
                        // StockId null ama yine de event logla (izlenebilirlik)
                        StockEvent event = new StockEvent();
                        event.setProductId(productId);
                        event.setEventType(StockEventType.QUANTITY_CHANGED);
                        event.setOldValue(item.getQuantity());
                        event.setNewValue(0);
                        event.setSource(StockEventSource.ORDER);
                        event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " teslim edildi (" + item.getQuantity() + " adet, stok kaydı yok)");
                        event.setOrderNumber(order.getOrderNumber());
                        stockEventRepository.save(event);
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stock deduction failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            }

            // Door payment → auto-complete payment with audit trail
            if (com.warehouse.util.OrderStatusMachine.isDoorPayment(order.getPaymentMethod())) {
                var tx = paymentRepo.findByOrderIdAndStatus(order.getId(), com.warehouse.enums.PaymentStatus.INITIATED);
                if (tx.isPresent()) {
                    var payment = tx.get();
                    payment.setStatus(com.warehouse.enums.PaymentStatus.SUCCESS);
                    payment.setPaidAt(java.time.LocalDateTime.now());
                    payment.setPaidAmount(order.getGrandTotal());
                    paymentRepo.save(payment);
                    statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                        order, "PAYMENT_INITIATED", "PAYMENT_SUCCESS",
                        CurrentUser.usernameOrSystem(), "ADMIN", "Kapıda ödeme tahsil edildi"));
                }
            }
        }

        // If CANCELLED → release reserved stock + log StockEvent
        if (newStatus == OrderStatus.CANCELLED) {
            try {
                var items = orderItemRepository.findByOrderId(order.getId());
                for (var item : items) {
                    Long pId = null;
                    try { pId = item.getProduct() != null ? item.getProduct().getId() : null; } catch (Exception ignored) {}

                    if (item.getStockId() != null) {
                        stockRepository.findById(item.getStockId()).ifPresent(stock -> {
                            int oldReserved = stock.getReservedQuantity();
                            stock.setReservedQuantity(Math.max(0, oldReserved - item.getQuantity()));
                            stockRepository.save(stock);

                            StockEvent event = new StockEvent();
                            event.setStockId(stock.getId());
                            event.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
                            event.setEventType(StockEventType.RELEASED);
                            event.setOldValue(oldReserved);
                            event.setNewValue(stock.getReservedQuantity());
                            event.setSource(StockEventSource.ORDER);
                            event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " iptal — " + item.getQuantity() + " adet serbest");
                            event.setOrderNumber(order.getOrderNumber());
                            stockEventRepository.save(event);
                        });
                    } else {
                        StockEvent event = new StockEvent();
                        event.setProductId(pId);
                        event.setEventType(StockEventType.RELEASED);
                        event.setOldValue(item.getQuantity());
                        event.setNewValue(0);
                        event.setSource(StockEventSource.ORDER);
                        event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " iptal — " + item.getQuantity() + " adet serbest (stok kaydı yok)");
                        event.setOrderNumber(order.getOrderNumber());
                        stockEventRepository.save(event);
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stock release failed for cancelled order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        // Send status update notification (email + SMS depending on preferences)
        try {
            if (order.getCustomer() != null) {
                notificationDispatchService.notifyOrderStatusChange(
                        order.getCustomer(),
                        order.getOrderNumber(),
                        newStatus.name(),
                        order.getCargoTrackingNo(),
                        body.getNote()
                );
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Sipariş durumu güncellendi: " + com.warehouse.util.OrderStatusMachine.getLabel(newStatus)));
    }

    @GetMapping("/{id}/allowed-transitions")
    public ResponseEntity<java.util.List<Map<String, String>>> getAllowedTransitions(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        java.util.List<Map<String, String>> transitions = com.warehouse.util.OrderStatusMachine.getAllowedTransitions(order.getStatus())
            .stream()
            .map(s -> Map.of("status", s.name(), "label", com.warehouse.util.OrderStatusMachine.getLabel(s)))
            .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(transitions);
    }

    @PutMapping("/{id}/cargo")
    public ResponseEntity<Map<String, String>> updateCargo(@PathVariable Long id,
                                                            @RequestBody OrderCargoUpdateRequest body) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        if (body.getCargoCompany() != null) {
            try { order.setCargoCompany(CargoCompany.valueOf(body.getCargoCompany())); } catch (Exception ignored) {}
        }
        order.setCargoTrackingNo(body.getCargoTrackingNo());
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("message", "Kargo bilgisi güncellendi."));
    }

    @PutMapping("/{id}/note")
    public ResponseEntity<Map<String, String>> updateNote(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));
        order.setAdminNote(body.get("note"));
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("message", "Not eklendi."));
    }

    @PutMapping("/{id}/confirm-payment")
    public ResponseEntity<Map<String, String>> confirmPayment(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        String admin = CurrentUser.usernameOrSystem();
        paymentService.confirmBankTransfer(id, admin);
        return ResponseEntity.ok(Map.of("message", "Havale/EFT ödemesi onaylandı."));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Map<String, String>> refundOrder(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body,
                                                            jakarta.servlet.http.HttpServletRequest request) {
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
        String reason = (String) body.getOrDefault("reason", "Admin iade");
        paymentService.initiateRefund(id, amount, reason, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "İade işlemi başlatıldı."));
    }

    // ==================== Excel Export ====================

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("tr-TR"));
    private static final String[] ORDER_EXPORT_HEADERS = {
        "Sipariş No", "Müşteri", "E-posta", "Tutar", "Durum", "Ödeme Yöntemi", "Kargo", "Tarih"
    };

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // Fetch all orders with customer eagerly loaded
        List<Order> allOrders = orderRepository.findAllWithCustomer(
                PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        // Apply optional filters
        if (status != null) {
            allOrders = allOrders.stream()
                    .filter(o -> o.getStatus() == status)
                    .collect(Collectors.toList());
        }
        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime from = LocalDate.parse(startDate).atStartOfDay();
            allOrders = allOrders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(from))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime to = LocalDate.parse(endDate).atTime(23, 59, 59);
            allOrders = allOrders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isAfter(to))
                    .collect(Collectors.toList());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Siparişler");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Data style
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < ORDER_EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(ORDER_EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIndex = 1;
            for (Order order : allOrders) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                setCellVal(row, col++, order.getOrderNumber(), dataStyle);
                setCellVal(row, col++, safeCustomerName(order), dataStyle);
                setCellVal(row, col++, safeCustomerEmail(order), dataStyle);
                setCellVal(row, col++, order.getGrandTotal() != null ? order.getGrandTotal().toPlainString() : "0", dataStyle);
                setCellVal(row, col++, order.getStatus() != null
                        ? com.warehouse.util.OrderStatusMachine.getLabel(order.getStatus()) : "", dataStyle);
                setCellVal(row, col++, order.getPaymentMethod() != null ? order.getPaymentMethod() : "", dataStyle);
                setCellVal(row, col++, order.getCargoCompany() != null ? order.getCargoCompany().name() : "", dataStyle);
                setCellVal(row, col, order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "", dataStyle);
            }

            // Auto-size columns
            for (int i = 0; i < ORDER_EXPORT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String filename = "siparisler-" + LocalDate.now().toString() + ".xlsx";
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(out.size())
                    .body(resource);

        } catch (IOException e) {
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Excel dosyası oluşturulurken hata oluştu.");
        }
    }

    private void setCellVal(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    // ==================== Invoice Upload ====================

    /**
     * Manuel fatura PDF/resim yükleme. Storage abstraction üzerinden
     * yazılır → dev'de local fs, prod'da Railway bucket / S3.
     * DB'de {@code order.invoiceUrl} alanı artık <strong>storage key</strong>
     * tutar (örn. {@code "invoices/123/abc.pdf"}), filesystem path değil.
     */
    @PostMapping("/{id}/invoice")
    public ResponseEntity<Map<String, String>> uploadInvoice(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("invoiceNumber") String invoiceNumber) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        if (file.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Dosya boş olamaz.");
        }

        try {
            String key = photoStorageService.storeDocument(
                    "invoices/" + order.getId(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream()
            );

            order.setInvoiceNumber(invoiceNumber);
            order.setInvoiceUrl(key);
            orderRepository.save(order);

            return ResponseEntity.ok(Map.of("message", "Fatura yüklendi.", "invoiceUrl", key));
        } catch (IOException e) {
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Fatura dosyası kaydedilirken hata oluştu.");
        }
    }

    // ==================== Invoice Download ====================

    /**
     * Fatura indirme/görüntüleme. Stream storage'tan çekilir, response'a
     * yazılır. {@code ?inline=true} → tarayıcıda görüntüle.
     */
    @GetMapping("/{id}/invoice/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadInvoice(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean inline) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        String key = order.getInvoiceUrl();
        if (key == null || key.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Bu siparişe ait fatura bulunamadı.");
        }

        try {
            // Legacy DB kayıtları için backward-compat:
            // Eski path'ler "uploads/invoices/..." veya "C:/...uploads/invoices/..." formatında
            // olabilir → key'e dönüştür (sadece dosya adını al, prefix'i invoices/{orderId} yap)
            String storageKey = key;
            if (key.contains("uploads/invoices/")) {
                String fileName = key.substring(key.lastIndexOf('/') + 1);
                storageKey = "invoices/" + order.getId() + "/" + fileName;
            }

            java.io.InputStream stream;
            try {
                stream = photoStorageService.openDocumentStream(storageKey);
            } catch (Exception e) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Fatura dosyası bulunamadı.");
            }

            byte[] bytes = stream.readAllBytes();
            stream.close();

            String ext = "";
            if (storageKey.contains(".")) ext = storageKey.substring(storageKey.lastIndexOf('.'));
            String filename = "fatura-" + order.getOrderNumber() + ext;

            String contentType = guessContentType(ext);
            String disposition = inline ? "inline; filename=" + filename : "attachment; filename=" + filename;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new org.springframework.core.io.ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Fatura dosyası indirilirken hata oluştu.");
        }
    }

    private String guessContentType(String ext) {
        return switch (ext.toLowerCase()) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".xml" -> "application/xml";
            default -> "application/octet-stream";
        };
    }

    private String safeCustomerName(Order o) {
        try { return o.getCustomer() != null ? o.getCustomer().getFirstName() + " " + o.getCustomer().getLastName() : ""; }
        catch (Exception e) { return ""; }
    }

    private String safeCustomerEmail(Order o) {
        try { return o.getCustomer() != null ? o.getCustomer().getEmail() : ""; }
        catch (Exception e) { return ""; }
    }

    private String safeCustomerPhone(Order o) {
        try { return o.getCustomer() != null ? o.getCustomer().getPhone() : ""; }
        catch (Exception e) { return ""; }
    }

    private int safeItemCount(Order o) {
        try { return o.getItems() != null ? o.getItems().size() : 0; }
        catch (Exception e) { return 0; }
    }
}
