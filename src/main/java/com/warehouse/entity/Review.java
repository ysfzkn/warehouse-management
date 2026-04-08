package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_reviews_product", columnList = "product_id"),
    @Index(name = "idx_reviews_customer", columnList = "customer_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Min(1) @Max(5)
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 200)
    private String title;

    @Column(length = 2000)
    private String comment;

    @Column(name = "is_approved", nullable = false)
    private boolean approved = false;

    @Column(name = "is_visible", nullable = false)
    private boolean visible = true;

    @Column(name = "admin_reply", length = 1000)
    private String adminReply;

    @Column(name = "admin_reply_at")
    private LocalDateTime adminReplyAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
