package com.warehouse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A single member line of a product set (bundle).
 *
 * Links a {@code BUNDLE} product ({@link #bundle}) to one of its member products
 * ({@link #product}) together with the quantity of that member contained in one
 * unit of the set.
 */
@Entity
@Table(
    name = "bundle_items",
    uniqueConstraints = @UniqueConstraint(name = "uq_bundle_items", columnNames = {"bundle_id", "product_id"}),
    indexes = {
        @Index(name = "idx_bundle_items_bundle", columnList = "bundle_id"),
        @Index(name = "idx_bundle_items_product", columnList = "product_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"bundle"})
public class BundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The owning set (product_type = BUNDLE). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private Product bundle;

    /** The member product included in the set (must be SIMPLE). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"stocks", "images", "bundleItems", "hibernateLazyInitializer", "handler"})
    private Product product;

    @Min(1)
    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * When true this member is a gift: it still ships with the set (consuming real
     * stock) but is excluded from the set's price total and shown with a "Hediye"
     * badge on the storefront.
     */
    @Column(name = "is_gift", nullable = false)
    private boolean gift = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
