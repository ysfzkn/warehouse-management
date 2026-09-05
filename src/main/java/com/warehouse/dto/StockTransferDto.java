package com.warehouse.dto;

import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferDto {
    private Long id;
    private SimpleWarehouseDto sourceWarehouse;
    private SimpleWarehouseDto destinationWarehouse;
    private SimpleProductDto product;
    private Integer quantity;
    private String driverName;
    private String driverTcId;
    private String driverPhone;
    private String vehiclePlate;
    /** Goods are out, carrier not recorded yet — the list renders a badge off this. */
    private boolean carrierPending;
    private String handoverToName;
    private String handoverToPhone;
    private String handedOverBy;
    private TransferStatus status;
    private TransferType transferType;
    private LocalDateTime transferDate;
    private LocalDateTime completedDate;
    private LocalDateTime cancelledDate;
    private String notes;
    private String cancellationReason;
    private String customerFullName;
    private String customerPhone;
    private String customerAddress;
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private LinkedCustomerDto linkedCustomer;
    private String completionNote;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer totalQuantity;
    private Integer uniqueProductCount;
    private List<TransferItemDto> items;
    private TransferApprovalStatus approvalStatus;
    private String approvalRequestedBy;
    private LocalDateTime approvalRequestedAt;
    private String approvalDecisionBy;
    private LocalDateTime approvalDecisionAt;
    private String approvalNote;
    private boolean deleteRequest;

    /** Populated only when the delivery is matched to an e-commerce customer. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedCustomerDto {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleWarehouseDto {
        private Long id;
        private String name;
        private String location;
        private String warehouseType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleProductDto {
        private Long id;
        private String name;
        private String sku;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferItemDto {
        private Long id;
        private Long stockId;
        private SimpleProductDto product;
        private Integer quantity;
        private String customerName;
        private String customerPhone;
    }
}

