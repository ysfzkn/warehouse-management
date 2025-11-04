package com.warehouse.dto;

import java.time.LocalDateTime;

public class StockImportHistoryDto {
    public Long id;
    public String originalFilename;
    public String storedFilename;
    public String contentType;
    public Long warehouseId;
    public String warehouseName;
    public Integer totalRows;
    public Integer createdProducts;
    public Integer updatedProducts;
    public Integer createdStocks;
    public Integer updatedStocks;
    public String status;
    public LocalDateTime createdAt;
}
