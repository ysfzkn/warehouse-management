package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_name", columnList = "name"),
        @Index(name = "idx_products_is_active", columnList = "is_active"),
        @Index(name = "idx_products_category_id", columnList = "category_id"),
        @Index(name = "idx_products_brand_id", columnList = "brand_id"),
        @Index(name = "idx_products_color_id", columnList = "color_id"),
        @Index(name = "idx_products_variant_group_id", columnList = "variant_group_id"),
        @Index(name = "idx_products_updated_at", columnList = "updated_at"),
        @Index(name = "idx_products_slug", columnList = "slug")
    }
)
@NamedEntityGraph(
        name = Product.GRAPH_WITH_RELATIONS,
        attributeNodes = {
                @NamedAttributeNode(value = "category", subgraph = "Product.category"),
                @NamedAttributeNode("brand"),
                @NamedAttributeNode("color")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "Product.category",
                        attributeNodes = {
                                @NamedAttributeNode("parent")
                        }
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Product {

    public static final String GRAPH_WITH_RELATIONS = "Product.with-relations";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    @Column(nullable = false)
    private String name;

    @Size(max = 5000, message = "Açıklama en fazla 5000 karakter olabilir")
    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
    @Column(nullable = false, unique = true)
    private String sku;

    @PositiveOrZero(message = "Price must be positive or zero")
    @Column(nullable = true, precision = 10, scale = 2)
    private BigDecimal price;

    @PositiveOrZero(message = "Weight must be positive or zero")
    private Double weight;

    @Size(max = 50, message = "Dimensions cannot exceed 50 characters")
    private String dimensions;

    @Column(name = "length_cm")
    private Double lengthCm;

    @Column(name = "width_cm")
    private Double widthCm;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "shipping_rate", precision = 10, scale = 2)
    private BigDecimal shippingRate; // per desi unit

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate; // VAT rate (%)

    @Column(name = "sct_rate", precision = 5, scale = 2)
    private BigDecimal sctRate; // SCT (special consumption tax) rate (%)

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    @Column(name = "short_description", length = 1000)
    private String shortDescription;

    /**
     * Structured technical specifications (JSONB). Array of groups:
     * [{ "title": "...", "items": [{ "label": "...", "value": "..." }] }]
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "technical_specs", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> technicalSpecs;

    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured = false;

    @JsonProperty("isNew")
    @Column(name = "is_new", nullable = false)
    private boolean isNew = false;

    @PositiveOrZero(message = "Sale price must be positive or zero")
    @Column(name = "sale_price", precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "sold_count", nullable = false)
    private Integer soldCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnoreProperties({"products", "children", "hibernateLazyInitializer", "handler"})
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    @JsonIgnoreProperties({"products", "hibernateLazyInitializer", "handler"})
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id")
    @JsonIgnoreProperties({"products", "hibernateLazyInitializer", "handler"})
    private Color color;

    /**
     * Shared id linking this product to its color variants — the same product sold in
     * different colors. Products with the same non-null variant_group_id are color
     * variants of each other; each keeps its own color, stock, images and storefront
     * page. Null = standalone product with no color siblings. Managed by the service
     * layer (see ProductServiceImpl#applyVariantGroup).
     */
    @Column(name = "variant_group_id")
    private Long variantGroupId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /** Own warranty in months (null = inherit from category). */
    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    /** Own warranty description, e.g. "24 ay üretici garantisi" (null = inherit from category). */
    @Column(name = "warranty_text", length = 500)
    private String warrantyText;

    /** SIMPLE (ordinary product) or BUNDLE (a set composed of member products). */
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.SIMPLE;

    /** Member lines when this product is a BUNDLE (empty/null for SIMPLE products). */
    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @JsonIgnore
    private List<BundleItem> bundleItems;

    /**
     * Write-only input channel for the admin set editor. Each entry is
     * {@code { "productId": <id>, "quantity": <n>, "sortOrder": <n> }}.
     * Deserialized from the request and turned into {@link BundleItem} rows by the
     * service; never persisted directly and not returned to clients (entities are
     * mapped to DTOs before serialization).
     */
    @Transient
    private List<java.util.Map<String, Object>> bundleMemberRefs;

    /**
     * Write-only input channel for the admin form's "Renk Varyantları" picker: the ids of
     * the other products that are the same product in different colors. Deserialized from
     * the request and turned into a shared {@link #variantGroupId} by the service
     * (see ProductServiceImpl#applyVariantGroup); never persisted directly.
     */
    @Transient
    private List<Long> variantSiblingIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Stock> stocks;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ProductImage> images;

    // Constructor with basic fields
    public Product(String name, String sku, BigDecimal price, Category category) {
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.category = category;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sku='" + sku + '\'' +
                ", price=" + price +
                ", category=" + (category != null ? category.getName() : "null") +
                ", brand=" + (brand != null ? brand.getName() : "null") +
                ", color=" + (color != null ? color.getName() : "null") +
                ", lengthCm=" + lengthCm +
                ", widthCm=" + widthCm +
                ", heightCm=" + heightCm +
                ", shippingRate=" + shippingRate +
                ", vatRate=" + vatRate +
                ", sctRate=" + sctRate +
                ", isActive=" + isActive +
                '}';
    }
}
