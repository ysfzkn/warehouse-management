package com.warehouse.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class StockFilter {

    public enum Status {
        ALL, LOW, OUT;

        public static Status from(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "LOW", "LOW-STOCK", "LOW_STOCK" -> LOW;
                case "OUT", "OUT_OF_STOCK", "OUT-OF-STOCK" -> OUT;
                default -> ALL;
            };
        }
    }

    private Long brandId;
    private Long colorId;
    private Long warehouseId;
    /**
     * Optional multi-warehouse filter. When provided and non-empty, it should take precedence over {@link #warehouseId}.
     * This enables filtering stock across multiple warehouses in a single query.
     */
    private List<Long> warehouseIds;
    private Long categoryId;
    private Long subCategoryId;
    private String search;
    private Status status = Status.ALL;
    private boolean reservedOnly;
    private boolean consignedOnly;
    private boolean hideOutOfStock;
    private LocalDateTime lastUpdatedFrom;
    private LocalDateTime lastUpdatedTo;

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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public List<Long> getWarehouseIds() {
        return warehouseIds;
    }

    public void setWarehouseIds(List<Long> warehouseIds) {
        this.warehouseIds = warehouseIds;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getSubCategoryId() {
        return subCategoryId;
    }

    public void setSubCategoryId(Long subCategoryId) {
        this.subCategoryId = subCategoryId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isReservedOnly() {
        return reservedOnly;
    }

    public void setReservedOnly(boolean reservedOnly) {
        this.reservedOnly = reservedOnly;
    }

    public boolean isConsignedOnly() {
        return consignedOnly;
    }

    public void setConsignedOnly(boolean consignedOnly) {
        this.consignedOnly = consignedOnly;
    }

    public boolean isHideOutOfStock() {
        return hideOutOfStock;
    }

    public void setHideOutOfStock(boolean hideOutOfStock) {
        this.hideOutOfStock = hideOutOfStock;
    }

    public LocalDateTime getLastUpdatedFrom() {
        return lastUpdatedFrom;
    }

    public void setLastUpdatedFrom(LocalDateTime lastUpdatedFrom) {
        this.lastUpdatedFrom = lastUpdatedFrom;
    }

    public LocalDateTime getLastUpdatedTo() {
        return lastUpdatedTo;
    }

    public void setLastUpdatedTo(LocalDateTime lastUpdatedTo) {
        this.lastUpdatedTo = lastUpdatedTo;
    }
}

