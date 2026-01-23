package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO for warehouse-level statistics.
 * Contains aggregated data per warehouse.
 */
public class WarehouseStatsDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("location")
    private String location;

    @JsonProperty("totalQuantity")
    private Long totalQuantity = 0L;

    @JsonProperty("reserved")
    private Long reserved = 0L;

    @JsonProperty("consigned")
    private Long consigned = 0L;

    @JsonProperty("productCount")
    private Long productCount = 0L;

    @JsonProperty("totalValue")
    private BigDecimal totalValue = BigDecimal.ZERO;

    public WarehouseStatsDto() {
    }

    public WarehouseStatsDto(Long id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Long getReserved() {
        return reserved;
    }

    public void setReserved(Long reserved) {
        this.reserved = reserved;
    }

    public Long getConsigned() {
        return consigned;
    }

    public void setConsigned(Long consigned) {
        this.consigned = consigned;
    }

    public Long getProductCount() {
        return productCount;
    }

    public void setProductCount(Long productCount) {
        this.productCount = productCount;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }
}
