package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    public String shortDescription;
    public java.util.List<java.util.Map<String, Object>> technicalSpecs;
    public BigDecimal salePrice;
    public LocalDateTime saleStart;
    public LocalDateTime saleEnd;
    public boolean featured;
    @JsonProperty("isNew")
    public boolean isNew;
    public String slug;
    public String metaTitle;
    public String metaDescription;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public Long totalQuantity;

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


