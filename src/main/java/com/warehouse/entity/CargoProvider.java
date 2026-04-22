package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cargo_providers", indexes = {
    @Index(name = "idx_cargo_providers_code", columnList = "code", unique = true),
    @Index(name = "idx_cargo_providers_active", columnList = "active")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CargoProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Size(max = 500)
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "base_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseCost;

    @DecimalMin("0.00")
    @Column(name = "cost_per_desi", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costPerDesi = BigDecimal.ZERO;

    @DecimalMin("0.00")
    @Column(name = "free_shipping_threshold", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal freeShippingThreshold = new BigDecimal("500.00");

    @Min(1) @Max(30)
    @Column(name = "estimated_delivery_days")
    @Builder.Default
    private Integer estimatedDeliveryDays = 3;

    @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(name = "vat_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("20.00");

    @Size(max = 500)
    @Column(name = "tracking_url_template", length = 500)
    private String trackingUrlTemplate;

    /**
     * Kargonomi tarafındaki carrier slug'ı ("yurtici", "aras", "mng" vb).
     * Müşteri bu provider'ı seçince Kargonomi API'ye bu slug ile "bu carrier ile gönder"
     * talimatı verilir. Boşsa Kargonomi otomatik (en ucuz) carrier seçer.
     */
    @Size(max = 40)
    @Column(name = "kargonomi_slug", length = 40)
    private String kargonomiSlug;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    /**
     * Calculate shipping cost for given desi and subtotal.
     */
    public BigDecimal calculateShippingCost(BigDecimal desi, BigDecimal subtotal) {
        if (freeShippingThreshold != null && subtotal != null
            && subtotal.compareTo(freeShippingThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = baseCost != null ? baseCost : BigDecimal.ZERO;
        if (costPerDesi != null && desi != null && desi.compareTo(BigDecimal.ZERO) > 0) {
            cost = cost.add(costPerDesi.multiply(desi));
        }
        return cost;
    }

    public BigDecimal calculateVat(BigDecimal shippingCost) {
        if (vatRate == null || shippingCost == null || shippingCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return shippingCost.multiply(vatRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }
}
