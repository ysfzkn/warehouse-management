package com.warehouse.dto.admin;

import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.OrderChannel;
import com.warehouse.enums.ManualPaymentState;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data @Builder
public class AdminOrderDetailDto {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Map<String, Object> shippingAddress;
    private Map<String, Object> billingAddress;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal vatTotal;
    private BigDecimal grandTotal;
    private String couponCode;
    private String paymentMethod;
    private OrderChannel orderChannel;
    private String channelReference;
    private String createdByAdmin;
    private ManualPaymentState manualPaymentState;
    private LocalDateTime paymentDueAt;
    private LocalDateTime paymentReminderAt;
    private LocalDateTime paymentReceivedAt;
    private LocalDateTime customerConfirmationExpiresAt;
    private LocalDateTime customerConfirmedAt;
    private Map<String, Object> legalConsentSnapshot;
    private String customerConfirmationIp;
    private Integer installmentCount;
    private String deliveryMethod;      // CARGO | OWN_TRANSFER
    private String cargoCompany;
    private String cargoProviderName;
    private String cargoTrackingNo;
    private LocalDate estimatedDeliveryDate;
    private String customerNote;
    private String adminNote;
    private String ipAddress;
    private String invoiceNumber;
    private String invoiceUrl;
    private List<OrderItemDto> items;
    private List<StatusHistoryDto> statusHistory;
    private LocalDateTime createdAt;

    // Critical admin info for bank transfer/EFT — used to reconcile against the bank statement
    private String bankTransferReference;   // HVL... — search for this code in the bank statement
    private LocalDateTime bankTransferDeadline;
    private String bankTransferStatus;      // INITIATED / SUCCESS / FAILED / TIMEOUT

    @Data @Builder
    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private String productName;
        private String productSku;
        private String imageUrl;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private Long warehouseId;
        private String warehouseName;
        private Long stockId;
    }

    @Data @Builder
    public static class StatusHistoryDto {
        private String oldStatus;
        private String newStatus;
        private String changedBy;
        private String note;
        private LocalDateTime createdAt;
    }
}
