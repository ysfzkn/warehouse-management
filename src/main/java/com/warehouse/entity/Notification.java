package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_created_at", columnList = "created_at"),
        @Index(name = "idx_notif_read", columnList = "is_read")
})
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // If null, visible to all admins; otherwise targeted to a specific user id
    @Column(name = "target_user_id")
    private Long targetUserId;

    // Deep link metadata
    @Column(name = "entity_type", length = 60)
    private String entityType; // e.g. "Stock" or "StockTransfer"

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


