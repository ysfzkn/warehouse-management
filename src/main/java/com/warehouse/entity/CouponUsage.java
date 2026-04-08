package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_usages", indexes = {
    @Index(name = "idx_coupon_usages_coupon", columnList = "coupon_id"),
    @Index(name = "idx_coupon_usages_customer", columnList = "customer_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CouponUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() { this.usedAt = LocalDateTime.now(); }
}
