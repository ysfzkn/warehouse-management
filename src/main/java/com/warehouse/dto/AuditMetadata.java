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
    private String customerName; // For EMANET_DEPO warehouses and transfer customer info
    private String customerPhone; // For EMANET_DEPO warehouses and transfer customer info
    private Long transferId; // Reference to transfer if this is a transfer-related stock movement
}

