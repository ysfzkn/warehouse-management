package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight identity holder for a color-variant group. Its only job is to mint a
 * shared id that {@link Product#getVariantGroupId()} rows point at, so that the same
 * product sold in several colors can be linked together. Group membership lives on the
 * products themselves; a group with fewer than two members is emptied by the service.
 */
@Entity
@Table(name = "product_variant_groups")
@Data
@NoArgsConstructor
public class VariantGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
