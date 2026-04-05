package com.warehouse.dto.admin;

import com.warehouse.enums.OrderStatus;
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
    private Integer installmentCount;
    private String cargoCompany;
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
