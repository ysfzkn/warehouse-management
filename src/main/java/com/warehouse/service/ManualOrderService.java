package com.warehouse.service;

import com.warehouse.dto.admin.ManualOrderRequest;
import com.warehouse.entity.*;
import com.warehouse.enums.*;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ManualOrderService {
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final StockRepository stocks;
    private final PaymentTransactionRepository payments;
    private final OrderStatusHistoryRepository history;
    private final PasswordEncoder passwordEncoder;
    private final com.warehouse.service.notification.NotificationDispatchService notificationDispatch;
    private final CmsPageRepository cmsPages;
    private static final List<String> LEGAL_SLUGS = List.of(
        "mesafeli-satis-sozlesmesi", "on-bilgilendirme-formu", "kvkk-aydinlatma-metni");

    public ManualOrderService(CustomerRepository customers, ProductRepository products, OrderRepository orders,
                              StockRepository stocks, PaymentTransactionRepository payments,
                              OrderStatusHistoryRepository history, PasswordEncoder passwordEncoder,
                              com.warehouse.service.notification.NotificationDispatchService notificationDispatch,
                              CmsPageRepository cmsPages) {
        this.customers = customers;
        this.products = products;
        this.orders = orders;
        this.stocks = stocks;
        this.payments = payments;
        this.history = history;
        this.passwordEncoder = passwordEncoder;
        this.notificationDispatch = notificationDispatch;
        this.cmsPages = cmsPages;
    }

    @Transactional
    public Order create(ManualOrderRequest request, String adminUsername) {
        validateRequest(request);
        Customer customer = resolveCustomer(request);
        String number = "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        Order order = new Order();
        order.setOrderNumber(number);
        order.setCustomer(customer);
        order.setOrderChannel(request.getChannel());
        order.setChannelReference(trim(request.getChannelReference()));
        order.setCreatedByAdmin(adminUsername);
        order.setManualPaymentState(request.getPaymentState());
        order.setPaymentDueAt(request.getPaymentDueAt());
        order.setPaymentReminderAt(request.getReminderAt());
        order.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase(Locale.ROOT));
        order.setShippingAddressSnapshot(new LinkedHashMap<>(request.getShippingAddress()));
        order.setBillingAddressSnapshot(request.getBillingAddress() == null
            ? new LinkedHashMap<>(request.getShippingAddress()) : new LinkedHashMap<>(request.getBillingAddress()));
        order.setCustomerNote(trim(request.getNote()));
        order.setAdminNote("Admin tarafından manuel oluşturuldu" + (request.getChannelReference() == null ? "" : " — ref: " + request.getChannelReference()));
        order.setDistanceSalesContractAccepted(false);
        order.setPreliminaryInfoAccepted(false);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setSctTotal(BigDecimal.ZERO);
        order.setShippingVat(BigDecimal.ZERO);
        order.setInstallmentCount(1);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<OrderItem> lines = new ArrayList<>();
        for (ManualOrderRequest.Item input : request.getItems()) {
            Product product = products.findById(input.getProductId()).orElseThrow(() ->
                new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ürün bulunamadı: " + input.getProductId()));
            BigDecimal price = input.getUnitPrice() != null ? input.getUnitPrice()
                : (product.getSalePrice() != null && product.getSalePrice().signum() > 0 ? product.getSalePrice() : product.getPrice());
            if (price == null || price.signum() < 0) throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçersiz ürün fiyatı");
            BigDecimal total = price.multiply(BigDecimal.valueOf(input.getQuantity()));
            BigDecimal vatRate = product.getVatRate() == null ? BigDecimal.ZERO : product.getVatRate();
            subtotal = subtotal.add(total);
            vatTotal = vatTotal.add(total.multiply(vatRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));

            List<Stock> available = stocks.findAvailableByProductForUpdate(product.getId());
            int remaining = input.getQuantity();
            Stock primary = available.stream().filter(s -> s.getAvailableQuantity() >= input.getQuantity()).findFirst().orElse(null);
            List<Map<String, Object>> allocations = new ArrayList<>();
            if (primary != null) {
                primary.setReservedQuantity((primary.getReservedQuantity() == null ? 0 : primary.getReservedQuantity()) + input.getQuantity());
                stocks.save(primary);
                allocations.add(Map.of("stockId", primary.getId(), "warehouseId", primary.getWarehouse().getId(), "quantity", input.getQuantity()));
                remaining = 0;
            }
            if (remaining > 0) throw new WarehouseManagementException(ErrorCode.STOCK_RESERVATION_FAILED,
                product.getName() + " için yeterli stok yok. Eksik: " + remaining);

            OrderItem line = new OrderItem();
            line.setProduct(product);
            line.setQuantity(input.getQuantity());
            line.setUnitPrice(price);
            line.setVatRate(vatRate);
            line.setSctRate(product.getSctRate() == null ? BigDecimal.ZERO : product.getSctRate());
            line.setDiscountAmount(BigDecimal.ZERO);
            line.setLineTotal(total);
            if (primary != null) {
                line.setStockId(primary.getId());
                line.setWarehouseId(primary.getWarehouse().getId());
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", product.getName());
            snapshot.put("sku", product.getSku());
            snapshot.put("price", price.toPlainString());
            snapshot.put("allocations", allocations);
            snapshot.put("manualPrice", input.getUnitPrice() != null);
            line.setProductSnapshot(snapshot);
            lines.add(line);
        }

        BigDecimal shipping = request.getShippingCost() == null ? BigDecimal.ZERO : request.getShippingCost();
        order.setSubtotal(subtotal);
        order.setVatTotal(vatTotal);
        order.setShippingCost(shipping);
        order.setGrandTotal(subtotal.add(vatTotal).add(shipping));
        boolean received = request.getPaymentState() == ManualPaymentState.RECEIVED;
        boolean settled = received || request.getPaymentState() == ManualPaymentState.NOT_REQUIRED;
        order.setStatus(settled ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT);
        if (received) order.setPaymentReceivedAt(LocalDateTime.now());
        order = orders.save(order);
        for (OrderItem line : lines) line.setOrder(order);
        order.setItems(lines);
        order = orders.save(order);

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setIdempotencyKey("manual:" + order.getOrderNumber());
        payment.setPaymentProvider(providerFor(order.getPaymentMethod()));
        payment.setStatus(settled ? PaymentStatus.SUCCESS : PaymentStatus.INITIATED);
        payment.setAmount(order.getGrandTotal());
        payment.setPaidAmount(received ? order.getGrandTotal() : (settled ? BigDecimal.ZERO : null));
        payment.setPaidAt(settled ? LocalDateTime.now() : null);
        payment.setExpiresAt(request.getPaymentDueAt());
        if (payment.getPaymentProvider() == PaymentProvider.BANK_TRANSFER) {
            payment.setBankTransferRef(request.getChannelReference() != null && !request.getChannelReference().isBlank()
                ? request.getChannelReference().trim() : order.getOrderNumber());
            order.setBankTransferDeadline(request.getPaymentDueAt());
        }
        payments.save(payment);

        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order);
        event.setNewStatus(order.getStatus().name());
        event.setChangedBy(adminUsername);
        event.setChangeSource("ADMIN_MANUAL");
        event.setNote("Manuel sipariş oluşturuldu — kanal: " + request.getChannel());
        history.save(event);
        return order;
    }

    @Transactional
    public Order markPaymentReceived(Long orderId, String adminUsername) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
            new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı"));
        if (order.getOrderChannel() == OrderChannel.ONLINE) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Online sipariş ödemesi ödeme sağlayıcısı üzerinden yönetilir");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Yalnızca ödeme bekleyen sipariş tahsil edildi olarak işaretlenebilir");
        }
        order.setManualPaymentState(ManualPaymentState.RECEIVED);
        order.setPaymentReceivedAt(LocalDateTime.now());
        order.setPaymentReminderSentAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PAID);
        payments.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED).ifPresent(tx -> {
            tx.setStatus(PaymentStatus.SUCCESS); tx.setPaidAmount(tx.getAmount()); tx.setPaidAt(LocalDateTime.now()); payments.save(tx);
        });
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order); event.setOldStatus(OrderStatus.PENDING_PAYMENT.name()); event.setNewStatus(OrderStatus.PAID.name());
        event.setChangedBy(adminUsername); event.setChangeSource("ADMIN_MANUAL"); event.setNote("Ödeme alındı olarak işaretlendi");
        history.save(event);
        Order saved = orders.save(order);
        notificationDispatch.notifyPaymentReceived(saved.getCustomer(), saved.getOrderNumber());
        return saved;
    }

    @Transactional
    public Order updatePaymentPlan(Long orderId, ManualPaymentState state, LocalDateTime dueAt,
                                   LocalDateTime reminderAt, String adminUsername) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
            new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı"));
        if (order.getOrderChannel() == OrderChannel.ONLINE) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Online siparişin ödeme planı bu ekrandan değiştirilemez");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Yalnızca ödeme bekleyen siparişin ödeme planı değiştirilebilir");
        }
        if (state != ManualPaymentState.WAITING && state != ManualPaymentState.SCHEDULED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ödeme planında yalnızca WAITING veya SCHEDULED kullanılabilir");
        }
        if (state == ManualPaymentState.SCHEDULED && dueAt == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Planlı ödeme için son ödeme tarihi zorunludur");
        }
        if (reminderAt != null && dueAt != null && reminderAt.isAfter(dueAt)) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Hatırlatma zamanı son ödeme tarihinden sonra olamaz");
        }
        order.setManualPaymentState(state);
        order.setPaymentDueAt(dueAt);
        order.setPaymentReminderAt(reminderAt);
        order.setPaymentReminderSentAt(null);
        if ("BANK_TRANSFER".equals(order.getPaymentMethod())) order.setBankTransferDeadline(dueAt);
        payments.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED).ifPresent(tx -> {
            tx.setExpiresAt(dueAt); payments.save(tx);
        });
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order); event.setNewStatus(order.getStatus().name()); event.setChangedBy(adminUsername);
        event.setChangeSource("ADMIN_MANUAL"); event.setNote("Ödeme planı güncellendi — " + state + ", vade: " + dueAt);
        history.save(event);
        return orders.save(order);
    }

    @Transactional
    public String createConfirmationToken(Long orderId, String adminUsername) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
            new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı"));
        if (order.getOrderChannel() == OrderChannel.ONLINE) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Online sipariş zaten checkout sırasında onaylanır");
        }
        if (order.getCustomerConfirmedAt() != null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş müşteri tarafından zaten onaylanmış");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.RETURNED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Bu durumdaki sipariş için onay bağlantısı üretilemez");
        }
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        order.setCustomerConfirmationTokenHash(hash(token));
        order.setCustomerConfirmationExpiresAt(LocalDateTime.now().plusDays(7));
        orders.save(order);
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order); event.setNewStatus(order.getStatus().name()); event.setChangedBy(adminUsername);
        event.setChangeSource("ADMIN_MANUAL"); event.setNote("Müşteri onay bağlantısı üretildi"); history.save(event);
        return token;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> confirmationPreview(String token) {
        Order order = confirmationOrder(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNumber", order.getOrderNumber());
        result.put("customerName", order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
        result.put("grandTotal", order.getGrandTotal());
        result.put("channel", order.getOrderChannel());
        result.put("expiresAt", order.getCustomerConfirmationExpiresAt());
        result.put("confirmedAt", order.getCustomerConfirmedAt());
        result.put("legalDocuments", order.getCustomerConfirmedAt() == null
            ? legalDocuments(false) : legalDocumentMetadata(order.getLegalConsentSnapshot()));
        result.put("items", order.getItems().stream().map(item -> Map.of(
            "name", String.valueOf(item.getProductSnapshot().getOrDefault("name", "Ürün")),
            "quantity", item.getQuantity(), "unitPrice", item.getUnitPrice(), "lineTotal", item.getLineTotal()
        )).toList());
        return result;
    }

    @Transactional
    public Order confirm(String token, Map<String, String> expectedHashes, String ipAddress, String userAgent) {
        Order order = confirmationOrder(token);
        if (order.getCustomerConfirmedAt() != null) return order;
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.RETURNED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "İptal veya iade edilmiş sipariş onaylanamaz");
        }
        Map<String, Object> legalSnapshot = legalDocuments(true);
        for (String slug : LEGAL_SLUGS) {
            Object raw = legalSnapshot.get(slug);
            if (!(raw instanceof Map<?, ?> doc)) continue;
            String currentHash = String.valueOf(doc.get("sha256"));
            if (expectedHashes == null || !currentHash.equalsIgnoreCase(expectedHashes.getOrDefault(slug, ""))) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Hukuki metinlerden biri görüntüledikten sonra güncellendi. Sayfayı yenileyip yeniden okuyun.");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        order.setDistanceSalesContractAccepted(true);
        order.setDistanceSalesContractAcceptedAt(now);
        order.setPreliminaryInfoAccepted(true);
        order.setPreliminaryInfoAcceptedAt(now);
        order.setKvkkConsentAt(now);
        order.setCustomerConfirmedAt(now);
        order.setLegalConsentSnapshot(legalSnapshot);
        order.setCustomerConfirmationIp(trimToLength(ipAddress, 45));
        order.setCustomerConfirmationUserAgent(trimToLength(userAgent, 500));
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order); event.setNewStatus(order.getStatus().name()); event.setChangedBy("customer");
        event.setChangeSource("CUSTOMER_CONFIRMATION"); event.setNote("Mesafeli satış, ön bilgilendirme ve KVKK metinleri müşteri tarafından onaylandı");
        history.save(event);
        return orders.save(order);
    }

    private Map<String, Object> legalDocuments(boolean includeContent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String slug : LEGAL_SLUGS) {
            CmsPage page = cmsPages.findBySlugAndActiveTrue(slug).orElseThrow(() ->
                new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Zorunlu hukuki metin yayında değil: " + slug));
            String content = page.getContent() == null ? "" : page.getContent();
            if (content.isBlank()) throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Zorunlu hukuki metin boş: " + slug);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("pageId", page.getId());
            doc.put("slug", page.getSlug());
            doc.put("title", page.getTitle());
            doc.put("updatedAt", page.getUpdatedAt() == null ? "" : page.getUpdatedAt().toString());
            doc.put("sha256", hash(content));
            if (includeContent) doc.put("content", content);
            snapshot.put(slug, doc);
        }
        return snapshot;
    }

    private Map<String, Object> legalDocumentMetadata(Map<String, Object> snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> metadata = new LinkedHashMap<>();
        snapshot.forEach((slug, raw) -> {
            if (raw instanceof Map<?, ?> source) {
                Map<String, Object> doc = new LinkedHashMap<>();
                for (String key : List.of("pageId", "slug", "title", "updatedAt", "sha256")) {
                    if (source.get(key) != null) doc.put(key, source.get(key));
                }
                metadata.put(slug, doc);
            }
        });
        return metadata;
    }

    private Order confirmationOrder(String token) {
        if (token == null || token.length() < 40) throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçersiz onay bağlantısı");
        Order order = orders.findByCustomerConfirmationTokenHash(hash(token)).orElseThrow(() ->
            new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Onay bağlantısı geçersiz veya daha önce kullanılmış"));
        if (order.getCustomerConfirmationExpiresAt() == null || order.getCustomerConfirmationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Onay bağlantısının süresi dolmuş");
        }
        return order;
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) { throw new IllegalStateException("Token özeti üretilemedi", e); }
    }

    private Customer resolveCustomer(ManualOrderRequest request) {
        if (request.getCustomerId() != null) return customers.findById(request.getCustomerId()).orElseThrow(() ->
            new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Müşteri bulunamadı"));
        String email = trim(request.getEmail());
        if (email != null) {
            Optional<Customer> existing = customers.findByEmail(email.toLowerCase(Locale.ROOT));
            if (existing.isPresent()) return existing.get();
        }
        Optional<Customer> byPhone = customers.findFirstByPhone(request.getPhone().trim());
        if (byPhone.isPresent()) return byPhone.get();
        Customer c = new Customer();
        c.setFirstName(request.getFirstName().trim()); c.setLastName(request.getLastName().trim()); c.setPhone(request.getPhone().trim());
        c.setEmail(email == null ? "manual+" + UUID.randomUUID() + "@local.invalid" : email.toLowerCase(Locale.ROOT));
        c.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); c.setActive(true); c.setEmailVerified(false);
        return customers.save(c);
    }

    private void validateRequest(ManualOrderRequest request) {
        if (request.getChannel() == OrderChannel.ONLINE) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Manuel sipariş kanalı ONLINE olamaz");
        }
        Set<String> allowedMethods = Set.of("BANK_TRANSFER", "DOOR_CASH", "DOOR_CARD");
        if (request.getPaymentMethod() == null || !allowedMethods.contains(request.getPaymentMethod().trim().toUpperCase(Locale.ROOT))) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Desteklenmeyen ödeme yöntemi");
        }
        if (request.getShippingCost() != null && request.getShippingCost().signum() < 0) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Kargo ücreti negatif olamaz");
        }
        if (request.getShippingAddress() == null || request.getShippingAddress().get("addressLine") == null
                || String.valueOf(request.getShippingAddress().get("addressLine")).isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Teslimat adresi zorunludur");
        }
        if (request.getPaymentState() == ManualPaymentState.SCHEDULED && request.getPaymentDueAt() == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Planlı ödeme için son ödeme tarihi zorunludur");
        }
        if (request.getReminderAt() != null && request.getPaymentDueAt() != null
                && request.getReminderAt().isAfter(request.getPaymentDueAt())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Hatırlatma zamanı son ödeme tarihinden sonra olamaz");
        }
    }

    private PaymentProvider providerFor(String method) {
        return switch (method) {
            case "BANK_TRANSFER" -> PaymentProvider.BANK_TRANSFER;
            case "DOOR_CASH", "DOOR_CARD" -> PaymentProvider.DOOR_PAYMENT;
            default -> PaymentProvider.BANK_TRANSFER;
        };
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String trimToLength(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
