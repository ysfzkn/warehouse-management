package com.warehouse.dto;

import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

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
    private String completionNote;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleWarehouseDto {
        private Long id;
        private String name;
        private String location;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleProductDto {
        private Long id;
        private String name;
        private String sku;
    }
}

