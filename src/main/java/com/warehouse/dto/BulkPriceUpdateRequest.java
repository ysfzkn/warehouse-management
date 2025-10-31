package com.warehouse.dto;

import java.math.BigDecimal;

public class BulkPriceUpdateRequest {

    // Filters (all optional)
    private Long categoryId;
    private Long brandId;
    private Long colorId;
    private boolean onlyActive = true;

    // Mode: percentage or amount
    private String mode; // "PERCENTAGE" or "AMOUNT"
    private BigDecimal value; // positive number
    private String direction; // "INCREASE" or "DECREASE"

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public Long getColorId() {
        return colorId;
    }

    public void setColorId(Long colorId) {
        this.colorId = colorId;
    }

    public boolean isOnlyActive() {
        return onlyActive;
    }

    public void setOnlyActive(boolean onlyActive) {
        this.onlyActive = onlyActive;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}


