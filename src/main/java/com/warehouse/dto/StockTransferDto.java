package com.warehouse.dto;

import com.warehouse.enums.TransferStatus;
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
    private LocalDateTime transferDate;
    private LocalDateTime completedDate;
    private LocalDateTime cancelledDate;
    private String notes;
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

