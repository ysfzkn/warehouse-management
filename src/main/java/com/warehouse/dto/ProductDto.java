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
    public Integer warrantyMonths;
    public String warrantyText;
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

    // Product sets (bundles)
    public String productType; // "SIMPLE" | "BUNDLE"
    public Integer bundleItemCount;
    public String primaryImageUrl; // set thumbnail (bundles only)
    public java.util.List<BundleItemDto> bundleItems;

    public static class BundleItemDto {
        public Long productId;
        public String name;
        public String sku;
        public Integer quantity;
        public Integer sortOrder;
        public BigDecimal price;
        public BigDecimal salePrice;
    }

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


