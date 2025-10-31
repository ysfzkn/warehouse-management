package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for failed row information during stock import.
 */
public class FailedRowInfo {

    @JsonProperty("rowNumber")
    private int rowNumber;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("reason")
    private String reason;

    public FailedRowInfo() {
    }

    public FailedRowInfo(int rowNumber, String productName, String sku, String reason) {
        this.rowNumber = rowNumber;
        this.productName = productName;
        this.sku = sku;
        this.reason = reason;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

