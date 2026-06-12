package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_images",
        indexes = {
                @Index(name = "idx_product_images_product_id", columnList = "product_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"product"})
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "relative_path", nullable = false, length = 500)
    private String relativePath;

    @Column(name = "thumbnail_path", nullable = false, length = 500)
    private String thumbnailPath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * Triple-image-set slot for product sets. {@code null} = normal gallery image;
     * {@code 1}, {@code 2}, {@code 3} = one of the three dedicated set slots shown
     * as a 3-column block on the storefront. Slot images are excluded from the
     * normal gallery's ordering and primary-image logic.
     */
    @Column(name = "slot")
    private Integer slot;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    /**
     * AI cover pipeline role. {@code null} = normal image;
     * {@code "COVER_INPUT"} = per-member input photo selected for AI cover
     * generation — must be excluded from the gallery, the storefront and all
     * primary-image resolution; {@code "AI_COVER"} = the AI-generated cover,
     * which behaves as a normal gallery image (marker is admin-UI only).
     */
    @Column(name = "ai_role", length = 20)
    private String aiRole;

    /** For COVER_INPUT rows: the bundle member product this input photo represents. */
    @Column(name = "member_product_id")
    private Long memberProductId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


