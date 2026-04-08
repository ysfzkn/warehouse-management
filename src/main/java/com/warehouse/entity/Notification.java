package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_created_at", columnList = "created_at"),
        @Index(name = "idx_notif_read", columnList = "is_read"),
        @Index(name = "idx_notif_actor", columnList = "actor"),
        @Index(name = "idx_notif_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_notif_source_warehouse_id", columnList = "source_warehouse_id"),
        @Index(name = "idx_notif_destination_warehouse_id", columnList = "destination_warehouse_id"),
        @Index(name = "idx_notif_product_sku", columnList = "product_sku")
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

    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "warehouse_name", length = 150)
    private String warehouseName;

    @Column(name = "source_warehouse_id")
    private Long sourceWarehouseId;

    @Column(name = "source_warehouse_name", length = 150)
    private String sourceWarehouseName;

    @Column(name = "destination_warehouse_id")
    private Long destinationWarehouseId;

    @Column(name = "destination_warehouse_name", length = 150)
    private String destinationWarehouseName;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "product_sku", length = 100)
    private String productSku;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


