package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTO for low stock items to reduce data transfer.
 */
public class LowStockItemDto {

    @JsonProperty("stockId")
    private Long stockId;

    @JsonProperty("productId")
    private Long productId;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("productSku")
    private String productSku;

    @JsonProperty("warehouseId")
    private Long warehouseId;

    @JsonProperty("warehouseName")
    private String warehouseName;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("minStockLevel")
    private Integer minStockLevel;

    @JsonProperty("brandId")
    private Long brandId;

    @JsonProperty("colorId")
    private Long colorId;

    public LowStockItemDto() {
    }

    public LowStockItemDto(Long stockId, Long productId, String productName, String productSku,
                           Long warehouseId, String warehouseName, Integer quantity, Integer minStockLevel,
                           Long brandId, Long colorId) {
        this.stockId = stockId;
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.quantity = quantity;
        this.minStockLevel = minStockLevel;
        this.brandId = brandId;
        this.colorId = colorId;
    }

    // Getters and Setters
    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getMinStockLevel() {
        return minStockLevel;
    }

    public void setMinStockLevel(Integer minStockLevel) {
        this.minStockLevel = minStockLevel;
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
}
