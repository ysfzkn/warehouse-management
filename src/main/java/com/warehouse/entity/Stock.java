package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "warehouse_id"})
})
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Warehouse is required")
    @ManyToOne(fetch = FetchType.LAZY)
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

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    // Default constructor
    public Stock() {
        this.lastUpdated = LocalDateTime.now();
    }

    // Constructor with product, warehouse, and quantity
    public Stock(Product product, Warehouse warehouse, Integer quantity) {
        this();
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    // Business logic methods
    public Integer getAvailableQuantity() {
        return Math.max(0, this.quantity - this.reservedQuantity);
    }

    public boolean isLowStock() {
        return this.quantity <= this.minStockLevel;
    }

    public boolean isOutOfStock() {
        return this.quantity == 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getMinStockLevel() {
        return minStockLevel;
    }

    public void setMinStockLevel(Integer minStockLevel) {
        this.minStockLevel = minStockLevel;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
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
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
