package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StoreProductDto {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String shortDescription;
    // Effective warranty (product value, else category, else parent category)
    private Integer warrantyMonths;
    private String warrantyText;
    private List<java.util.Map<String, Object>> technicalSpecs;
    private String sku;
    private BigDecimal price;
    private BigDecimal salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;
    private BigDecimal vatRate;
    private BigDecimal sctRate;
    private BigDecimal shippingRate;
    private Double weight;
    private Double lengthCm;
    private Double widthCm;
    private Double heightCm;
    private boolean featured;
    private boolean isNew;

    // Relationships
    private String categoryName;
    private String categorySlug;
    private String brandName;
    private String brandSlug;
    private String colorName;
    private String colorHexCode;

    // Stock
    private String stockStatus; // IN_STOCK, OUT_OF_STOCK, LOW_STOCK
    private int availableQuantity;

    // Images
    private List<ImageDto> images;
    private String primaryImageUrl;
    // Triple image set (product sets) — up to 3 dedicated slot images shown as a 3-column block
    private List<ImageDto> setImages;

    // Reviews
    private Double averageRating;
    private long reviewCount;

    // Product set (bundle)
    private String productType; // "SIMPLE" | "BUNDLE"
    private Integer bundleItemCount;
    private BigDecimal membersOriginalTotal; // sum of members' current prices × qty (for "save X" display)
    private List<StoreBundleItem> bundleItems; // members (detail only)

    @Data
    @Builder
    public static class StoreBundleItem {
        private Long productId;
        private String slug;
        private String name;
        private String primaryImageUrl;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal salePrice;
        private String stockStatus;
        private boolean gift;
    }

    @Data
    @Builder
    public static class ImageDto {
        private Long id;
        private String url;
        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private int sortOrder;
        private boolean primary;
        private Integer slot;
    }
}
