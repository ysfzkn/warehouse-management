package com.warehouse.dto;

import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import java.time.LocalDateTime;

public class StockTransferFilter {

    private TransferStatus status;
    private TransferType transferType;
    private Long sourceWarehouseId;
    private Long destinationWarehouseId;
    private String productName;
    private String sku;
    private String driverName;
    private String notes;
    private LocalDateTime transferDateFrom;
    private LocalDateTime transferDateTo;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public void setTransferType(TransferType transferType) {
        this.transferType = transferType;
    }

    public Long getSourceWarehouseId() {
        return sourceWarehouseId;
    }

    public void setSourceWarehouseId(Long sourceWarehouseId) {
        this.sourceWarehouseId = sourceWarehouseId;
    }

    public Long getDestinationWarehouseId() {
        return destinationWarehouseId;
    }

    public void setDestinationWarehouseId(Long destinationWarehouseId) {
        this.destinationWarehouseId = destinationWarehouseId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getTransferDateFrom() {
        return transferDateFrom;
    }

    public void setTransferDateFrom(LocalDateTime transferDateFrom) {
        this.transferDateFrom = transferDateFrom;
    }

    public LocalDateTime getTransferDateTo() {
        return transferDateTo;
    }

    public void setTransferDateTo(LocalDateTime transferDateTo) {
        this.transferDateTo = transferDateTo;
    }

    public LocalDateTime getCreatedAtFrom() {
        return createdAtFrom;
    }

    public void setCreatedAtFrom(LocalDateTime createdAtFrom) {
        this.createdAtFrom = createdAtFrom;
    }

    public LocalDateTime getCreatedAtTo() {
        return createdAtTo;
    }

    public void setCreatedAtTo(LocalDateTime createdAtTo) {
        this.createdAtTo = createdAtTo;
    }
}

