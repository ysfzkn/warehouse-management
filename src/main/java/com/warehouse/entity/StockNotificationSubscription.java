package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stokta yoksa bildir - müşteri aboneliği.
 * Müşteri stoku tükenmiş bir ürün için bildirim talep ettiğinde oluşturulur.
 * Ürün tekrar stoğa girdiğinde abonelere e-posta gönderilir ve notified=true olarak işaretlenir.
 */
@Entity
@Table(name = "stock_notification_subscriptions", indexes = {
        @Index(name = "idx_stock_notif_product", columnList = "product_id"),
        @Index(name = "idx_stock_notif_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Opsiyonel: misafir kullanıcılar için null olabilir */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private boolean notified = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
