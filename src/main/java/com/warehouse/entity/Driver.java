package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A driver used on stock transfers.
 *
 * <p>Driver details used to be retyped on every single transfer, which is both slow and how the
 * same person ends up recorded three different ways. Every transfer now upserts its driver here,
 * so the directory fills itself from real usage and the transfer form can offer them back.</p>
 *
 * <p>The phone number is the identity: TC numbers are often left blank and names are written
 * inconsistently, but a driver's phone is both unique and the thing warehouse staff remember.</p>
 */
@Entity
@Table(name = "drivers", indexes = {
    @Index(name = "idx_drivers_search", columnList = "search_text"),
    @Index(name = "idx_drivers_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Şoför adı zorunludur")
    @Size(min = 3, max = 100, message = "Şoför adı 3-100 karakter olmalıdır")
    @Column(nullable = false, length = 100)
    private String name;

    @Pattern(regexp = "^$|^[0-9]{11}$", message = "TC kimlik no 11 haneli olmalıdır")
    @Column(name = "tc_id", length = 11)
    private String tcId;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    @Size(max = 20)
    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    /**
     * Name, phone, TC and plate folded onto ASCII, so "Ballı" finds "Balli".
     * Maintained by the service on every write — never set this by hand.
     */
    @Size(max = 400)
    @Column(name = "search_text", length = 400)
    private String searchText;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** How many transfers this driver has carried; drives the "most used first" ordering. */
    @Column(name = "transfer_count", nullable = false)
    @Builder.Default
    private Integer transferCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
