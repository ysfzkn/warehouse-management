package com.warehouse.entity;

import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.CargoCompany;
import com.warehouse.enums.DeliveryMethod;
import com.warehouse.enums.OrderChannel;
import com.warehouse.enums.ManualPaymentState;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_customer", columnList = "customer_id"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_number", columnList = "order_number"),
    @Index(name = "idx_orders_created", columnList = "created_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_channel", nullable = false, length = 30)
    private OrderChannel orderChannel = OrderChannel.ONLINE;

    @Column(name = "channel_reference", length = 100)
    private String channelReference;

    @Column(name = "created_by_admin", length = 150)
    private String createdByAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_payment_state", length = 30)
    private ManualPaymentState manualPaymentState;

    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;

    @Column(name = "payment_reminder_at")
    private LocalDateTime paymentReminderAt;

    @Column(name = "payment_reminder_sent_at")
    private LocalDateTime paymentReminderSentAt;

    @Column(name = "payment_received_at")
    private LocalDateTime paymentReceivedAt;

    @Column(name = "customer_confirmation_token_hash", length = 64)
    private String customerConfirmationTokenHash;

    @Column(name = "customer_confirmation_expires_at")
    private LocalDateTime customerConfirmationExpiresAt;

    @Column(name = "customer_confirmed_at")
    private LocalDateTime customerConfirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legal_consent_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> legalConsentSnapshot;

    @Column(name = "customer_confirmation_ip", length = 45)
    private String customerConfirmationIp;

    @Column(name = "customer_confirmation_user_agent", length = 500)
    private String customerConfirmationUserAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddressSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_address_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> billingAddressSnapshot;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "vat_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal vatTotal = BigDecimal.ZERO;

    @Column(name = "sct_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal sctTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount", precision = 10, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount = 1;

    /** Cargo hand-off vs. delivery by our own vehicle (see StockTransfer.orderId). */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 30)
    private DeliveryMethod deliveryMethod = DeliveryMethod.CARGO;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_company", length = 50)
    private CargoCompany cargoCompany;

    @Column(name = "cargo_tracking_no", length = 100)
    private String cargoTrackingNo;

    @Column(name = "cargo_provider_id")
    private Long cargoProviderId;

    @Column(name = "cargo_provider_name", length = 100)
    private String cargoProviderName;

    /** Shipment ID returned by Kargonomi / other API providers (for cancellation/tracking) */
    @Column(name = "cargo_provider_shipment_id", length = 100)
    private String cargoProviderShipmentId;

    /** Shipping label PDF URL (for printing) */
    @Column(name = "cargo_label_url", length = 500)
    private String cargoLabelUrl;

    /** Timestamp of the last shipment tracking query */
    @Column(name = "cargo_last_tracked_at")
    private LocalDateTime cargoLastTrackedAt;

    @Column(name = "shipping_vat", precision = 10, scale = 2)
    private BigDecimal shippingVat = BigDecimal.ZERO;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "actual_delivery_date")
    private LocalDate actualDeliveryDate;

    @Column(name = "stock_transfer_id")
    private Long stockTransferId;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "distance_sales_contract_accepted", nullable = false)
    private boolean distanceSalesContractAccepted = false;

    @Column(name = "distance_sales_contract_accepted_at")
    private LocalDateTime distanceSalesContractAcceptedAt;

    @Column(name = "preliminary_info_accepted", nullable = false)
    private boolean preliminaryInfoAccepted = false;

    @Column(name = "preliminary_info_accepted_at")
    private LocalDateTime preliminaryInfoAcceptedAt;

    /**
     * Timestamp when KVKK consent was given at order time.
     * Critical evidence for guest checkout (for authenticated users the Customer entity
     * also has kvkkConsentAt; this column is the snapshot taken at order time).
     */
    @Column(name = "kvkk_consent_at")
    private LocalDateTime kvkkConsentAt;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "bank_transfer_deadline")
    private LocalDateTime bankTransferDeadline;

    /**
     * Proof of ownership for the public payment-initialisation endpoint.
     *
     * <p>Guests pay without an account, so that endpoint cannot require a session — but
     * it also must not accept a bare, guessable {@code orderId}. Checkout mints a
     * high-entropy token here and returns it once, to the browser that placed the order;
     * initialising payment requires it (or an authenticated customer who owns the order).
     * Never serialised: it is handed over in the checkout response only.</p>
     */
    @JsonIgnore
    @Column(name = "payment_access_token", length = 64)
    private String paymentAccessToken;

    @JsonIgnore
    @Column(name = "payment_access_token_expires_at")
    private LocalDateTime paymentAccessTokenExpiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderItem> items;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
