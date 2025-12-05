package com.warehouse.entity;

import com.warehouse.enums.AuditAction;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_audit_source_warehouse_id", columnList = "source_warehouse_id"),
        @Index(name = "idx_audit_destination_warehouse_id", columnList = "destination_warehouse_id"),
        @Index(name = "idx_audit_product_sku", columnList = "product_sku")
})
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    private String username;

    @Column(name = "details", length = 2000)
    private String details;

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


