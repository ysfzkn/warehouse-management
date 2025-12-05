package com.warehouse.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Builder-style request for creating rich notifications with metadata.
 */
@Data
@Builder
public class NotificationRequest {
    private String title;
    private String message;
    private String entityType;
    private Long entityId;
    private String actor;
    private Long warehouseId;
    private String warehouseName;
    private Long sourceWarehouseId;
    private String sourceWarehouseName;
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
}

