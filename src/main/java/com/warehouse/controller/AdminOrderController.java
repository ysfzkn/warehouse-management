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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    public AdminOrderController(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderStatusHistoryRepository statusHistoryRepository,
                                 PaymentService paymentService,
                                 AdminSecurityService adminSecurityService,
                                 com.warehouse.repository.PaymentTransactionRepository paymentRepo,
                                 com.warehouse.repository.StockRepository stockRepository,
                                 StockEventRepository stockEventRepository,
                                 EmailService emailService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.paymentService = paymentService;
        this.adminSecurityService = adminSecurityService;
        this.paymentRepo = paymentRepo;
        this.stockRepository = stockRepository;
        this.stockEventRepository = stockEventRepository;
        this.emailService = emailService;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<AdminOrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<Order> result = orderRepository.findAllWithCustomer(PageRequest.of(page, size, sort));

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
            .items(items.stream().map(i -> AdminOrderDetailDto.OrderItemDto.builder()
                .id(i.getId())
                .productName(i.getProductSnapshot() != null ? (String) i.getProductSnapshot().get("name") : "")
                .productSku(i.getProductSnapshot() != null ? (String) i.getProductSnapshot().get("sku") : "")
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .lineTotal(i.getLineTotal())
                .build()).collect(Collectors.toList()))
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

        // If DELIVERED → deduct reserved stock (convert reservation to actual sale) + log StockEvent
        if (newStatus == OrderStatus.DELIVERED) {
            try {
                var items = orderItemRepository.findByOrderId(order.getId());
                for (var item : items) {
                    if (item.getStockId() != null) {
                        stockRepository.findById(item.getStockId()).ifPresent(stock -> {
                            int qty = item.getQuantity();
                            int oldQty = stock.getQuantity();
                            stock.setQuantity(Math.max(0, oldQty - qty));
                            stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - qty));
                            stockRepository.save(stock);

                            // Log stock event for traceability
                            StockEvent event = new StockEvent();
                            event.setStockId(stock.getId());
                            event.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
                            event.setEventType(StockEventType.QUANTITY_CHANGED);
                            event.setOldValue(oldQty);
                            event.setNewValue(stock.getQuantity());
                            event.setSource(StockEventSource.ORDER);
                            event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " teslim edildi (" + qty + " adet)");
                            stockEventRepository.save(event);
                        });
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
                            event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " iptal edildi (" + item.getQuantity() + " adet serbest)");
                            stockEventRepository.save(event);
                        });
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stock release failed for cancelled order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        // Send status update email to customer
        try {
            String custEmail = safeCustomerEmail(order);
            String custName = safeCustomerName(order);
            if (!custEmail.isEmpty()) {
                emailService.sendOrderStatusUpdate(custEmail, custName, order.getOrderNumber(),
                        com.warehouse.util.OrderStatusMachine.getLabel(newStatus), body.getNote());
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
