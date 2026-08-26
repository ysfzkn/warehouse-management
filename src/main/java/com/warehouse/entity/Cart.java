package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "carts")
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Coupon applied to this cart. Kept as a code rather than an FK so an admin can delete or
     * deactivate a coupon without breaking existing carts — it is re-validated on every read
     * and dropped silently once it stops qualifying.
     */
    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    /**
     * Timestamp when the abandoned-cart reminder email was sent.
     * If null, it has not been sent yet. Prevents duplicate sends.
     */
    @Column(name = "abandoned_cart_reminder_sent_at")
    private LocalDateTime abandonedCartReminderSentAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<CartItem> items;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
