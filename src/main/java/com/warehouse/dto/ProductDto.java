package com.warehouse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDto {
    public Long id;
    public String name;
    public String sku;
    public String description;
    public BigDecimal price;
    public boolean active;
    public Long categoryId;
    public String categoryName;
    public Long categoryParentId;
    public String categoryParentName;
    public Long brandId;
    public String brandName;
    public Long colorId;
    public String colorName;
    public CategoryInfo category;
    public BrandInfo brand;
    public ColorInfo color;
    public Double weight;
    public String dimensions;
    public Double lengthCm;
    public Double widthCm;
    public Double heightCm;
    public BigDecimal shippingRate;
    public BigDecimal vatRate;
    public BigDecimal sctRate;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static class CategoryInfo {
        public Long id;
        public String name;
        public ParentInfo parent;
    }

    public static class ParentInfo {
        public Long id;
        public String name;
    }

    public static class BrandInfo {
        public Long id;
        public String name;
    }

    public static class ColorInfo {
        public Long id;
        public String name;
        public String hexCode;
    }
}


