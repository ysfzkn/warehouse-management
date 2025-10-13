package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "warehouse_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Warehouse is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false)
    private Integer quantity = 0;

    @Min(value = 0, message = "Minimum stock level cannot be negative")
    @Column(name = "min_stock_level")
    private Integer minStockLevel = 0;

    @Min(value = 0, message = "Reserved quantity cannot be negative")
    @Column(name = "reserved_quantity")
    private Integer reservedQuantity = 0;

    @Min(value = 0, message = "Consigned quantity cannot be negative")
    @Column(name = "consigned_quantity")
    private Integer consignedQuantity = 0; // Emanet miktar

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    // Constructor with product, warehouse, and quantity
    public Stock(Product product, Warehouse warehouse, Integer quantity) {
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
        this.lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.lastUpdated = LocalDateTime.now();
    }

    // Business logic methods
    public Integer getAvailableQuantity() {
        int consigned = this.consignedQuantity != null ? this.consignedQuantity : 0;
        int reserved = this.reservedQuantity != null ? this.reservedQuantity : 0;
        return (this.quantity != null ? this.quantity : 0) - reserved - consigned;
    }

    public boolean isLowStock() {
        return this.quantity <= this.minStockLevel;
    }

    public boolean isOutOfStock() {
        return this.quantity == 0;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "id=" + id +
                ", product=" + (product != null ? product.getName() : "null") +
                ", warehouse=" + (warehouse != null ? warehouse.getName() : "null") +
                ", quantity=" + quantity +
                ", available=" + getAvailableQuantity() +
                ", minStockLevel=" + minStockLevel +
                ", reservedQuantity=" + reservedQuantity +
                ", consignedQuantity=" + consignedQuantity +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
