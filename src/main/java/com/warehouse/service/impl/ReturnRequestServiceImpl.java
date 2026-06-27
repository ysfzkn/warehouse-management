package com.warehouse.service.impl;

import com.warehouse.entity.*;
import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.ReturnReason;
import com.warehouse.enums.ReturnStatus;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.service.EmailService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.PaymentService;
import com.warehouse.service.ReturnRequestService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.OrderStatusHistoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ReturnRequestServiceImpl implements ReturnRequestService {

    private static final Logger log = LoggerFactory.getLogger(ReturnRequestServiceImpl.class);

    /** Non-terminal statuses that count as an "open" return for duplicate-prevention. */
    private static final List<ReturnStatus> OPEN_STATUSES = List.of(
            ReturnStatus.PENDING, ReturnStatus.APPROVED, ReturnStatus.CARGO_WAITING,
            ReturnStatus.RECEIVED, ReturnStatus.REFUND_PROCESSING);

    private final ReturnRequestRepository returnRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final CustomerRepository customerRepo;
    private final OrderStatusHistoryRepository statusHistoryRepo;
    private final StockRepository stockRepository;
    private final StockEventRepository stockEventRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final com.warehouse.repository.ReturnRequestPhotoRepository photoRepo;
    private final com.warehouse.service.PhotoStorageService photoStorageService;

    private static final int MAX_PHOTOS = 6;

    public ReturnRequestServiceImpl(ReturnRequestRepository returnRepo, OrderRepository orderRepo,
                                    OrderItemRepository orderItemRepo, CustomerRepository customerRepo,
                                    OrderStatusHistoryRepository statusHistoryRepo,
                                    StockRepository stockRepository, StockEventRepository stockEventRepository,
                                    PaymentService paymentService, EmailService emailService,
                                    NotificationService notificationService,
                                    com.warehouse.repository.ReturnRequestPhotoRepository photoRepo,
                                    com.warehouse.service.PhotoStorageService photoStorageService) {
        this.returnRepo = returnRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.customerRepo = customerRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.stockRepository = stockRepository;
        this.stockEventRepository = stockEventRepository;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.photoRepo = photoRepo;
        this.photoStorageService = photoStorageService;
    }

    // ─────────────────────────── create ───────────────────────────

    @Override
    public ReturnRequest createReturn(Long customerId, String orderNumber, ReturnReason reason,
                                      String customerNote, List<ReturnItemRequest> itemReqs) {
        Order order = orderRepo.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
            throw new WarehouseManagementException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new WarehouseManagementException(ErrorCode.RETURN_NOT_ALLOWED_FOR_STATUS);
        }
        if (!returnRepo.findByOrderIdAndStatusIn(order.getId(), OPEN_STATUSES).isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.RETURN_ALREADY_EXISTS);
        }
        if (reason == null) reason = ReturnReason.OTHER;

        List<OrderItem> orderItems = orderItemRepo.findByOrderId(order.getId());

        ReturnRequest rr = new ReturnRequest();
        rr.setReturnNumber(generateReturnNumber());
        rr.setOrder(order);
        rr.setCustomer(order.getCustomer());
        rr.setStatus(ReturnStatus.PENDING);
        rr.setReason(reason);
        rr.setCustomerNote(customerNote);

        List<ReturnRequestItem> items = new ArrayList<>();
        BigDecimal refundEstimate = BigDecimal.ZERO;

        if (itemReqs == null || itemReqs.isEmpty()) {
            // Default: return the whole order, full quantities.
            for (OrderItem oi : orderItems) {
                items.add(buildItem(rr, oi, oi.getQuantity(), null));
                refundEstimate = refundEstimate.add(lineRefund(oi, oi.getQuantity()));
            }
        } else {
            for (ReturnItemRequest req : itemReqs) {
                OrderItem oi = orderItems.stream()
                        .filter(x -> x.getId().equals(req.orderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new WarehouseManagementException(ErrorCode.RETURN_NO_ITEMS));
                int qty = Math.max(1, Math.min(req.quantity(), oi.getQuantity()));
                items.add(buildItem(rr, oi, qty, req.reason()));
                refundEstimate = refundEstimate.add(lineRefund(oi, qty));
            }
        }
        if (items.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.RETURN_NO_ITEMS);
        }
        rr.setItems(items);
        rr.setRefundAmount(refundEstimate);
        ReturnRequest saved = returnRepo.save(rr);

        // Reflect on the order + history trail.
        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepo.save(order);
        statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                order, old, OrderStatus.RETURN_REQUESTED, "customer", "CUSTOMER",
                "İade talebi " + saved.getReturnNumber() + " — " + reasonLabel(reason)
                        + (customerNote != null && !customerNote.isBlank() ? " (" + customerNote + ")" : "")));

        // Notify admin in-app; never let notification failures abort the request.
        try {
            notificationService.create(
                    "Yeni iade talebi: " + saved.getReturnNumber(),
                    "Sipariş " + order.getOrderNumber() + " için iade talebi oluşturuldu (" + reasonLabel(reason) + ").",
                    "RETURN_REQUEST", saved.getId());
        } catch (Exception e) {
            log.warn("Return admin notification failed for {}: {}", saved.getReturnNumber(), e.getMessage());
        }
        emailCustomerSafe(order, saved, "Talebiniz alındı",
                "İade talebiniz alındı ve incelemeye alındı. En kısa sürede size dönüş yapacağız.");
        log.info("Return {} created for order {} ({} items)", saved.getReturnNumber(), order.getOrderNumber(), items.size());
        return saved;
    }

    // ─────────────────────────── reads ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequest> listForCustomer(Long customerId, Pageable pageable) {
        return returnRepo.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequest getForCustomer(Long customerId, String returnNumber) {
        ReturnRequest rr = returnRepo.findByReturnNumber(returnNumber)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.RETURN_NOT_FOUND));
        if (rr.getCustomer() == null || !rr.getCustomer().getId().equals(customerId)) {
            throw new WarehouseManagementException(ErrorCode.RETURN_NOT_FOUND);
        }
        return rr;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequest> listForAdmin(ReturnStatus status, Pageable pageable) {
        return status != null ? returnRepo.findByStatus(status, pageable) : returnRepo.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequest getByIdForAdmin(Long id) {
        return returnRepo.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.RETURN_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(ReturnStatus status) {
        return returnRepo.countByStatus(status);
    }

    // ─────────────────────────── transitions ───────────────────────────

    @Override
    public ReturnRequest approve(Long id, String adminNote) {
        ReturnRequest rr = getByIdForAdmin(id);
        require(rr, ReturnStatus.PENDING);
        rr.setStatus(ReturnStatus.APPROVED);
        appendAdminNote(rr, adminNote);
        ReturnRequest saved = returnRepo.save(rr);
        emailCustomerSafe(rr.getOrder(), rr, "İadeniz onaylandı",
                "İade talebiniz onaylandı. Lütfen ürünleri belirtilen adrese kargolayın; kargo bilgisi tarafımıza ulaştığında süreci ilerleteceğiz."
                        + noteSuffix(adminNote));
        return saved;
    }

    @Override
    public ReturnRequest reject(Long id, String adminNote) {
        ReturnRequest rr = getByIdForAdmin(id);
        require(rr, ReturnStatus.PENDING);
        rr.setStatus(ReturnStatus.REJECTED);
        appendAdminNote(rr, adminNote);
        ReturnRequest saved = returnRepo.save(rr);

        // Roll the order back out of the return state (the customer keeps the product).
        Order order = rr.getOrder();
        if (order != null && order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepo.save(order);
            statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                    order, OrderStatus.RETURN_REQUESTED, OrderStatus.DELIVERED,
                    CurrentUser.usernameOrSystem(), "ADMIN",
                    "İade reddedildi (" + rr.getReturnNumber() + ")" + noteSuffix(adminNote)));
        }
        emailCustomerSafe(order, rr, "İade talebiniz reddedildi",
                "İade talebiniz değerlendirildi ve onaylanmadı." + noteSuffix(adminNote));
        return saved;
    }

    @Override
    public ReturnRequest markReceived(Long id, String adminNote) {
        ReturnRequest rr = getByIdForAdmin(id);
        if (rr.getStatus() != ReturnStatus.APPROVED && rr.getStatus() != ReturnStatus.CARGO_WAITING) {
            throw new WarehouseManagementException(ErrorCode.RETURN_INVALID_TRANSITION);
        }
        rr.setStatus(ReturnStatus.RECEIVED);
        appendAdminNote(rr, adminNote);
        ReturnRequest saved = returnRepo.save(rr);

        restock(rr);

        Order order = rr.getOrder();
        if (order != null) {
            OrderStatus old = order.getStatus();
            order.setStatus(OrderStatus.RETURNED);
            orderRepo.save(order);
            statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                    order, old, OrderStatus.RETURNED, CurrentUser.usernameOrSystem(), "ADMIN",
                    "İade ürünleri teslim alındı (" + rr.getReturnNumber() + ")"));
        }
        emailCustomerSafe(order, rr, "İade ürünleriniz teslim alındı",
                "İade ettiğiniz ürünler tarafımıza ulaştı. İade tutarınız en kısa sürede iade edilecektir." + noteSuffix(adminNote));
        return saved;
    }

    @Override
    public ReturnRequest refund(Long id, BigDecimal amount, String adminNote, String ipAddress) {
        ReturnRequest rr = getByIdForAdmin(id);
        if (rr.getStatus() != ReturnStatus.RECEIVED && rr.getStatus() != ReturnStatus.REFUND_PROCESSING) {
            throw new WarehouseManagementException(ErrorCode.RETURN_INVALID_TRANSITION);
        }
        Order order = rr.getOrder();
        BigDecimal refundAmount = (amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                ? amount
                : (rr.getRefundAmount() != null ? rr.getRefundAmount() : BigDecimal.ZERO);

        // Delegate to the payment gateway; PaymentService also flips the order to REFUNDED on full refund.
        if (order != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.initiateRefund(order.getId(), refundAmount,
                    "İade " + rr.getReturnNumber() + (adminNote != null ? " — " + adminNote : ""), ipAddress);
        }

        rr.setStatus(ReturnStatus.REFUNDED);
        rr.setRefundAmount(refundAmount);
        appendAdminNote(rr, adminNote);
        ReturnRequest saved = returnRepo.save(rr);

        emailCustomerSafe(order, rr, "İade tutarınız iade edildi",
                "İade tutarınız (" + refundAmount.toPlainString() + " TL) ödeme yönteminize iade edilmiştir. "
                        + "Bankanıza yansıması birkaç iş günü sürebilir." + noteSuffix(adminNote));
        return saved;
    }

    // ─────────────────────────── photos ───────────────────────────

    @Override
    public Long addPhoto(Long customerId, String returnNumber, String fileName, String contentType,
                         java.io.InputStream inputStream) {
        ReturnRequest rr = getForCustomer(customerId, returnNumber);
        if (photoRepo.countByReturnRequestId(rr.getId()) >= MAX_PHOTOS) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE,
                    "En fazla " + MAX_PHOTOS + " fotoğraf yükleyebilirsiniz.");
        }
        String key = photoStorageService.storeDocument(
                "return-photos/" + rr.getId(), fileName, contentType, inputStream);
        ReturnRequestPhoto photo = new ReturnRequestPhoto();
        photo.setReturnRequest(rr);
        photo.setStorageKey(key);
        photo.setFileName(fileName);
        photo.setContentType(contentType);
        return photoRepo.save(photo).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoData getPhoto(Long photoId) {
        ReturnRequestPhoto photo = photoRepo.findById(photoId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.RETURN_NOT_FOUND));
        try (java.io.InputStream in = photoStorageService.openDocumentStream(photo.getStorageKey())) {
            byte[] bytes = in.readAllBytes();
            String ct = photo.getContentType() != null ? photo.getContentType() : "application/octet-stream";
            return new PhotoData(bytes, ct, photo.getFileName());
        } catch (Exception e) {
            throw new WarehouseManagementException(ErrorCode.RETURN_NOT_FOUND);
        }
    }

    // ─────────────────────────── admin reply ───────────────────────────

    @Override
    public ReturnRequest reply(Long id, String message) {
        ReturnRequest rr = getByIdForAdmin(id);
        if (message == null || message.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, "Mesaj");
        }
        rr.setAdminNote(message.trim());
        ReturnRequest saved = returnRepo.save(rr);
        emailCustomerSafe(rr.getOrder(), rr, "İade talebinize yanıt", message.trim());
        return saved;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ReturnRequestItem buildItem(ReturnRequest rr, OrderItem oi, int qty, String reason) {
        ReturnRequestItem item = new ReturnRequestItem();
        item.setReturnRequest(rr);
        item.setOrderItem(oi);
        item.setProduct(oi.getProduct());
        item.setQuantity(qty);
        item.setReason(reason);
        return item;
    }

    private BigDecimal lineRefund(OrderItem oi, int qty) {
        if (oi.getUnitPrice() != null) {
            return oi.getUnitPrice().multiply(BigDecimal.valueOf(qty));
        }
        if (oi.getLineTotal() != null && oi.getQuantity() != null && oi.getQuantity() > 0) {
            return oi.getLineTotal().multiply(BigDecimal.valueOf(qty))
                    .divide(BigDecimal.valueOf(oi.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** Add returned quantities back into stock and log a traceable StockEvent. */
    private void restock(ReturnRequest rr) {
        List<ReturnRequestItem> items = rr.getItems();
        if (items == null) return;
        String orderNo = rr.getOrder() != null ? rr.getOrder().getOrderNumber() : "?";
        for (ReturnRequestItem item : items) {
            OrderItem oi = item.getOrderItem();
            if (oi == null || oi.getStockId() == null) continue;
            final int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty <= 0) continue;
            stockRepository.findById(oi.getStockId()).ifPresent(stock -> {
                int oldQty = stock.getQuantity();
                stock.setQuantity(oldQty + qty);
                stockRepository.save(stock);

                StockEvent event = new StockEvent();
                event.setStockId(stock.getId());
                event.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
                event.setEventType(StockEventType.QUANTITY_CHANGED);
                event.setOldValue(oldQty);
                event.setNewValue(stock.getQuantity());
                event.setSource(StockEventSource.ORDER);
                event.setSourceDetail("İade " + rr.getReturnNumber() + " teslim alındı (+" + qty + " adet)");
                event.setOrderNumber(orderNo);
                stockEventRepository.save(event);
            });
        }
    }

    private void require(ReturnRequest rr, ReturnStatus expected) {
        if (rr.getStatus() != expected) {
            throw new WarehouseManagementException(ErrorCode.RETURN_INVALID_TRANSITION);
        }
    }

    private void appendAdminNote(ReturnRequest rr, String note) {
        if (note != null && !note.isBlank()) {
            rr.setAdminNote(note.trim());
        }
    }

    private String noteSuffix(String note) {
        return (note != null && !note.isBlank()) ? " Not: " + note.trim() : "";
    }

    /** Sequential, zero-padded return number (RET-000001). Unique index guards races. */
    private String generateReturnNumber() {
        long next = returnRepo.count() + 1;
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = String.format("RET-%06d", next + attempt);
            if (returnRepo.findByReturnNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "RET-" + System.nanoTime();
    }

    private void emailCustomerSafe(Order order, ReturnRequest rr, String statusLabel, String message) {
        try {
            Customer c = rr.getCustomer();
            String email = c != null ? c.getEmail() : null;
            if (email == null || email.isBlank()) return;
            String name = c.getFirstName() != null ? c.getFirstName() : "Müşterimiz";
            String orderNo = order != null ? order.getOrderNumber() : "";
            emailService.sendReturnStatusUpdate(email, name, rr.getReturnNumber(), orderNo, statusLabel, message);
        } catch (Exception e) {
            log.warn("Return status email failed for {}: {}", rr.getReturnNumber(), e.getMessage());
        }
    }

    private String reasonLabel(ReturnReason r) {
        if (r == null) return "Diğer";
        return switch (r) {
            case DEFECTIVE -> "Arızalı / Kusurlu ürün";
            case WRONG_PRODUCT -> "Yanlış ürün gönderildi";
            case NOT_AS_DESCRIBED -> "Açıklamaya uymuyor";
            case CHANGED_MIND -> "Vazgeçtim";
            case DAMAGED_IN_SHIPPING -> "Kargoda hasar gördü";
            case OTHER -> "Diğer";
        };
    }
}
