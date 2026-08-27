package com.warehouse.dto;

import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.StockRequestType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO for StockRequest entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequestDto {
    private Long id;
    private Long stockId;
    private String productName;
    private String productSku;
    private String warehouseName;
    private StockRequestType type;
    private Integer quantity;
    private StockRequestStatus status;
    private String requestedBy;
    private OffsetDateTime requestedAt;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String rejectionReason;
    private String notes;
    private String irsaliyeNo;
    private java.time.LocalDate irsaliyeDate;
    private Integer currentStockQuantity;
    private Integer availableQuantity;
}





