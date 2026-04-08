package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_refresh_tokens")
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CustomerRefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isValid() { return !isExpired() && !isRevoked(); }
}
