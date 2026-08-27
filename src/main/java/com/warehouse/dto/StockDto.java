package com.warehouse.dto;

public class StockDto {

    public Long id;
    public Integer quantity;
    public Integer reservedQuantity;
    public Integer consignedQuantity;
    public Integer minStockLevel;
    public Integer availableQuantity;
    public java.time.LocalDateTime lastUpdated;
    public String additionNote;
    public String customerName;
    public String customerPhone;
    public String irsaliyeNo;
    public java.time.LocalDate irsaliyeDate;
    public ProductDto product;
    public WarehouseDto warehouse;

    public static class ProductDto {
        public Long id;
        public String name;
        public String sku;
    }

    public static class WarehouseDto {
        public Long id;
        public String name;
        public String location;
        public String warehouseType;
    }
}


