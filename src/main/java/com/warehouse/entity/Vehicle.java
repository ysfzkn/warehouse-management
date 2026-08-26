package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * A vehicle used on stock transfers.
 *
 * <p>The plate used to live as a single free-text field on the driver, which cannot represent
 * reality: a driver takes a different vehicle depending on the day and the load. Vehicles are
 * their own records now, assigned to drivers many-to-many.</p>
 */
@Entity
@Table(name = "vehicles", indexes = {
    @Index(name = "idx_vehicles_plate_key", columnList = "plate_key", unique = true),
    @Index(name = "idx_vehicles_search", columnList = "search_text"),
    @Index(name = "idx_vehicles_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plate as it should be read: upper-cased, spacing as the operator typed it. */
    @NotBlank(message = "Plaka zorunludur")
    @Size(max = 20, message = "Plaka en fazla 20 karakter olabilir")
    @Column(nullable = false, length = 20)
    private String plate;

    /**
     * Plate without spaces or punctuation, upper-cased. Identity is built on this so
     * "51 TV 51", "51tv51" and "51-TV-51" cannot become three vehicles.
     */
    @Size(max = 20)
    @Column(name = "plate_key", nullable = false, length = 20)
    private String plateKey;

    @Size(max = 100)
    @Column(name = "brand_model", length = 100)
    private String brandModel;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    /** Plate folded onto ASCII for the shared search behaviour. */
    @Size(max = 400)
    @Column(name = "search_text", length = 400)
    private String searchText;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "transfer_count", nullable = false)
    @Builder.Default
    private Integer transferCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Single place the identity key is derived, so every write path agrees on it. */
    public static String toPlateKey(String raw) {
        if (raw == null) return null;
        String key = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.forLanguageTag("tr-TR"));
        return key.isEmpty() ? null : key;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
