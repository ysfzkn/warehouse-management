package com.warehouse.service.impl;

import com.warehouse.entity.*;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.*;
import com.warehouse.service.CustomerAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KVKK Article 11 (e) + GDPR Article 17/20 service.
 * Holds the data export + account anonymization logic.
 *
 * <p><b>Anonymization principle:</b> The customer's personal data (first name,
 * last name, email, phone, etc.) is converted to an "anonymous" format, while
 * order/invoice records are retained as required by Turkey's legal retention
 * obligation (10 years). customer.email is changed to the
 * "anon-{hash}@deleted.local" format so the UNIQUE constraint is not violated
 * and a new account can later be opened with the same email.</p>
 */
@Service
public class CustomerAccountServiceImpl implements CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountServiceImpl.class);

    // Placeholder used instead of the customer first/last name in anonymized order records
    private static final String ANON_PREFIX = "[Anonim Müşteri]";

    private final CustomerRepository customerRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final CustomerAddressRepository addressRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final WishlistRepository wishlistRepo;
    private final ReviewRepository reviewRepo;
    private final NotificationPreferenceRepository notifPrefRepo;
    private final StockNotificationSubscriptionRepository stockNotifRepo;
    private final CustomerRefreshTokenRepository refreshTokenRepo;

    public CustomerAccountServiceImpl(CustomerRepository customerRepo,
                                       OrderRepository orderRepo,
                                       OrderItemRepository orderItemRepo,
                                       CustomerAddressRepository addressRepo,
                                       CartRepository cartRepo,
                                       CartItemRepository cartItemRepo,
                                       WishlistRepository wishlistRepo,
                                       ReviewRepository reviewRepo,
                                       NotificationPreferenceRepository notifPrefRepo,
                                       StockNotificationSubscriptionRepository stockNotifRepo,
                                       CustomerRefreshTokenRepository refreshTokenRepo) {
        this.customerRepo = customerRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.addressRepo = addressRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.wishlistRepo = wishlistRepo;
        this.reviewRepo = reviewRepo;
        this.notifPrefRepo = notifPrefRepo;
        this.stockNotifRepo = stockNotifRepo;
        this.refreshTokenRepo = refreshTokenRepo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data Export (GDPR Article 20: data portability)
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public Map<String, Object> exportCustomerData(Long customerId) {
        Customer c = customerRepo.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı: " + customerId));

        c.setDataExportRequestedAt(LocalDateTime.now());
        customerRepo.save(c);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now().toString());
        export.put("kvkkNote", "Bu dosya KVKK Madde 11 (e) ve GDPR Article 20 kapsamında kişisel verilerinizin ihracıdır.");

        // ── Profile ──
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", c.getId());
        profile.put("email", c.getEmail());
        profile.put("firstName", c.getFirstName());
        profile.put("lastName", c.getLastName());
        profile.put("phone", c.getPhone());
        profile.put("gender", c.getGender());
        profile.put("birthDate", c.getBirthDate() != null ? c.getBirthDate().toString() : null);
        profile.put("emailVerified", c.isEmailVerified());
        profile.put("kvkkConsent", c.isKvkkConsent());
        profile.put("kvkkConsentAt", asString(c.getKvkkConsentAt()));
        profile.put("marketingConsent", c.isMarketingConsent());
        profile.put("marketingConsentAt", asString(c.getMarketingConsentAt()));
        profile.put("createdAt", asString(c.getCreatedAt()));
        profile.put("lastLoginAt", asString(c.getLastLoginAt()));
        export.put("profile", profile);

        // ── Addresses ──
        List<CustomerAddress> addresses = addressRepo.findByCustomerId(customerId);
        export.put("addresses", addresses.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", a.getTitle());
            m.put("firstName", a.getFirstName());
            m.put("lastName", a.getLastName());
            m.put("phone", a.getPhone());
            m.put("addressLine", a.getAddressLine());
            m.put("city", a.getCity());
            m.put("district", a.getDistrict());
            m.put("postalCode", a.getPostalCode());
            m.put("isDefault", a.isDefault());
            return m;
        }).collect(Collectors.toList()));

        // ── Orders ──
        List<Order> orders = orderRepo.findByCustomerId(customerId);
        export.put("orders", orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNumber", o.getOrderNumber());
            m.put("status", o.getStatus() != null ? o.getStatus().name() : null);
            m.put("subtotal", o.getSubtotal());
            m.put("shippingCost", o.getShippingCost());
            m.put("vatTotal", o.getVatTotal());
            m.put("grandTotal", o.getGrandTotal());
            m.put("paymentMethod", o.getPaymentMethod());
            m.put("createdAt", asString(o.getCreatedAt()));
            m.put("invoiceNumber", o.getInvoiceNumber());
            // Order items
            List<OrderItem> items = orderItemRepo.findByOrderId(o.getId());
            m.put("items", items.stream().map(it -> {
                Map<String, Object> itm = new LinkedHashMap<>();
                String name = "";
                try {
                    // Prefer the snapshot, then the associated product
                    if (it.getProductSnapshot() != null && it.getProductSnapshot().get("name") != null) {
                        name = String.valueOf(it.getProductSnapshot().get("name"));
                    } else if (it.getProduct() != null) {
                        name = it.getProduct().getName();
                    }
                } catch (Exception ignored) {}
                itm.put("productName", name);
                itm.put("quantity", it.getQuantity());
                itm.put("unitPrice", it.getUnitPrice());
                itm.put("lineTotal", it.getLineTotal());
                return itm;
            }).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList()));

        // ── Reviews ──
        List<Review> reviews = reviewRepo.findAllByCustomerId(customerId);
        export.put("reviews", reviews.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", r.getProduct() != null ? r.getProduct().getId() : null);
            m.put("rating", r.getRating());
            m.put("comment", r.getComment());
            m.put("approved", r.isApproved());
            m.put("createdAt", asString(r.getCreatedAt()));
            return m;
        }).collect(Collectors.toList()));

        // ── Notification preferences ──
        export.put("notificationPreferences", notifPrefRepo.findByCustomerId(customerId));

        return export;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Account Anonymization (GDPR Article 17: right to erasure)
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public DeletionResult anonymizeAccount(Long customerId, String reason) {
        Customer c = customerRepo.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı: " + customerId));

        if (c.getAnonymizedAt() != null) {
            throw new AccountDeletionBlockedException("Hesap zaten anonimleştirilmiş.");
        }

        // ── 1) Active order check ──
        List<Order> orders = orderRepo.findByCustomerId(customerId);
        EnumSet<OrderStatus> activeStatuses = EnumSet.of(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED
        );
        boolean hasActiveOrder = orders.stream().anyMatch(o -> o.getStatus() != null && activeStatuses.contains(o.getStatus()));
        if (hasActiveOrder) {
            throw new AccountDeletionBlockedException(
                    "Aktif siparişiniz var. Sipariş tamamlanana veya iptal edilene kadar hesap silinemez.");
        }

        // ── 2) Revoke refresh tokens (terminate sessions) ──
        try { refreshTokenRepo.deleteByCustomerId(customerId); } catch (Exception ignored) {}

        // ── 3) Clear cart ──
        try {
            cartRepo.findByCustomerId(customerId).ifPresent(cart -> {
                cartItemRepo.deleteByCartId(cart.getId());
                cartRepo.delete(cart);
            });
        } catch (Exception e) { log.warn("Sepet silme hatası: {}", e.getMessage()); }

        // ── 4) Delete wishlist ──
        try { wishlistRepo.deleteByCustomerId(customerId); } catch (Exception e) { log.warn("Wishlist silme hatası: {}", e.getMessage()); }

        // ── 5) Delete addresses (snapshot addresses inside order items are retained) ──
        try {
            List<CustomerAddress> addresses = addressRepo.findByCustomerId(customerId);
            addressRepo.deleteAll(addresses);
        } catch (Exception e) { log.warn("Adres silme hatası: {}", e.getMessage()); }

        // ── 6) Anonymize reviews (not delete, since the product rating must be preserved) ──
        try {
            List<Review> reviews = reviewRepo.findAllByCustomerId(customerId);
            for (Review r : reviews) {
                // Keep the customer relation but change the display name; we do not put the customer name on the review
                r.setApproved(r.isApproved()); // no-op, only to set the dirty flag
            }
            reviewRepo.saveAll(reviews);
        } catch (Exception e) { log.warn("Yorum anonimleştirme hatası: {}", e.getMessage()); }

        // ── 7) Delete notification preferences and stock notification subscriptions ──
        try { notifPrefRepo.deleteByCustomerId(customerId); } catch (Exception ignored) {}
        try { stockNotifRepo.deleteByCustomerId(customerId); } catch (Exception ignored) {}

        // ── 8) Anonymize the customer's PII ──
        String anonHash = sha256(c.getEmail() + ":" + System.currentTimeMillis()).substring(0, 12);
        String anonEmail = "anon-" + anonHash + "@deleted.local";
        c.setEmail(anonEmail);
        c.setFirstName(ANON_PREFIX);
        c.setLastName("");
        c.setPhone(null);
        c.setBirthDate(null);
        c.setGender(null);
        c.setPasswordHash("DISABLED-" + UUID.randomUUID());
        c.setPasswordResetToken(null);
        c.setEmailVerifyToken(null);
        c.setActive(false);
        c.setEmailVerified(false);
        c.setMarketingConsent(false);
        // KVKK consent proof is retained (evidence for the period the data was processed)
        LocalDateTime now = LocalDateTime.now();
        c.setAnonymizedAt(now);
        c.setDataDeletionRequestedAt(now);
        if (reason != null && !reason.isBlank()) {
            c.setStatusNote("Hesap silme talebi: " + reason);
        }
        customerRepo.save(c);

        log.info("[KVKK] Müşteri anonimleştirildi: id={}, korunan sipariş={}", customerId, orders.size());
        return new DeletionResult(now, orders.size());
    }

    // ─── Helpers ───
    private static String asString(LocalDateTime t) { return t != null ? t.toString() : null; }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
