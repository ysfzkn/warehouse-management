package com.warehouse.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Structured context information captured alongside audit logs and notifications.
 */
@Data
@Builder
public class AuditMetadata {
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
    private String customerName;
    private String customerPhone;
    private Long transferId;
    private String note;
}

