package com.warehouse.entity;

import com.warehouse.enums.CmsPageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cms_pages", indexes = {
    @Index(name = "idx_cms_pages_slug", columnList = "slug")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CmsPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Column(nullable = false, length = 200)
    private String title;

    @NotBlank @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_type", nullable = false, length = 20)
    private CmsPageType pageType = CmsPageType.CONTENT;

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl;

    @Column(name = "banner_link", length = 500)
    private String bannerLink;

    @Column(name = "banner_position", length = 30)
    private String bannerPosition;

    @Column(name = "banner_start")
    private LocalDateTime bannerStart;

    @Column(name = "banner_end")
    private LocalDateTime bannerEnd;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
