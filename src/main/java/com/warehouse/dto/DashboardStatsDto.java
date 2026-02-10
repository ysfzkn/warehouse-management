package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO for dashboard statistics with optimized aggregated data.
 * This prevents loading all products and stocks into memory on the frontend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    @JsonProperty("totalWarehouses")
    private Long totalWarehouses = 0L;

    @JsonProperty("activeWarehouses")
    private Long activeWarehouses = 0L;

    @JsonProperty("totalProducts")
    private Long totalProducts = 0L;

    @JsonProperty("totalCategories")
    private Long totalCategories = 0L;

    @JsonProperty("totalBrands")
    private Long totalBrands = 0L;

    @JsonProperty("totalColors")
    private Long totalColors = 0L;

    @JsonProperty("lowStockItems")
    private Long lowStockItems = 0L;

    @JsonProperty("outOfStockItems")
    private Long outOfStockItems = 0L;

    @JsonProperty("totalStockQuantity")
    private Long totalStockQuantity = 0L;

    @JsonProperty("totalReserved")
    private Long totalReserved = 0L;

    @JsonProperty("totalConsigned")
    private Long totalConsigned = 0L;

    @JsonProperty("totalStockValue")
    private BigDecimal totalStockValue = BigDecimal.ZERO;
}
