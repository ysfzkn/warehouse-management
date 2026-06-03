package com.warehouse.dto.store;

import com.warehouse.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Simplified order info for public order tracking.
 * Contains no sensitive data (address, invoice number, contact) — returns status info only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOrderTrackingDto {

    private String orderNumber;
    private OrderStatus status;
    private String statusLabel;
    private LocalDateTime createdAt;

    // Delivery estimate
    private LocalDate estimatedDeliveryDate;
    private LocalDate actualDeliveryDate;

    // Shipment info
    private String cargoCompany;
    private String cargoProviderName;
    private String cargoTrackingNo;
    private String cargoTrackingUrl;

    // Grand total (for the customer to verify their order)
    private BigDecimal grandTotal;

    // Item count (summary)
    private Integer itemCount;

    // Masked customer name (e.g., "Ahmet Y***")
    private String maskedCustomerName;

    // Status history timeline
    private List<StatusHistoryItem> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryItem {
        private String status;
        private String statusLabel;
        private LocalDateTime changedAt;
        private String note;
    }
}
