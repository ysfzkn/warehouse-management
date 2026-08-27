package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "stocks",
    indexes = {
        @Index(name = "idx_stocks_product_id", columnList = "product_id"),
        @Index(name = "idx_stocks_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_stocks_last_updated", columnList = "last_updated"),
        @Index(name = "idx_stocks_quantity", columnList = "quantity"),
        @Index(name = "idx_stocks_customer_name", columnList = "customer_name"),
        @Index(name = "idx_stocks_irsaliye_key", columnList = "irsaliye_key"),
        @Index(name = "idx_stocks_irsaliye_date", columnList = "irsaliye_date")
    }
)
@NamedEntityGraph(
        name = Stock.GRAPH_WITH_PRODUCT_AND_WAREHOUSE,
        attributeNodes = {
                @NamedAttributeNode(value = "product", subgraph = "Stock.product"),
                @NamedAttributeNode("warehouse")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "Stock.product",
                        attributeNodes = {
                                @NamedAttributeNode("brand"),
                                @NamedAttributeNode("color")
                        }
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Stock {

    public static final String GRAPH_WITH_PRODUCT_AND_WAREHOUSE = "Stock.with-product-warehouse";

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
    private Integer consignedQuantity = 0; // Consigned quantity

    @Size(max = 500, message = "Addition note cannot exceed 500 characters")
    @Column(name = "addition_note", length = 500)
    private String additionNote;

    @Size(max = 255, message = "Customer name cannot exceed 255 characters")
    @Column(name = "customer_name", length = 255)
    private String customerName; // For EMANET_DEPO warehouses

    @Size(max = 20, message = "Customer phone cannot exceed 20 characters")
    @Column(name = "customer_phone", length = 20)
    private String customerPhone; // For EMANET_DEPO warehouses

    /**
     * Consignment customer name + phone folded onto ASCII, so the stock screen finds
     * "Fehmi Ballı" when the record was typed "Fehmi Balli". Maintained on every write.
     */
    @Size(max = 400)
    @Column(name = "customer_search", length = 400)
    private String customerSearch;

    /**
     * Waybill this stock came in on. Kept as the operator typed it — the paper is the reference,
     * so it should read back the same way.
     */
    @Size(max = 50, message = "İrsaliye numarası en fazla 50 karakter olabilir")
    @Column(name = "irsaliye_no", length = 50)
    private String irsaliyeNo;

    /**
     * {@link #irsaliyeNo} without punctuation and upper-cased, so "ABC 2026-14" and "abc202614"
     * find each other. Maintained on every write — never set this by hand.
     */
    @Size(max = 50)
    @Column(name = "irsaliye_key", length = 50)
    private String irsaliyeKey;

    /**
     * The date printed on the waybill, which is not the same as when the row was created — goods
     * are often booked in a day or two later, and it is the paper's date that gets reconciled.
     */
    @Column(name = "irsaliye_date")
    private java.time.LocalDate irsaliyeDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

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
        refreshSearchColumn();
    }

    @PrePersist
    protected void onCreate() {
        this.lastUpdated = LocalDateTime.now();
        refreshSearchColumn();
    }

    /** See {@link #customerSearch} — kept in step with every write, from any code path. */
    private void refreshSearchColumn() {
        this.customerSearch = com.warehouse.util.TurkishText.normalizeForSearch(
                this.customerName, this.customerPhone);
        this.irsaliyeKey = toIrsaliyeKey(this.irsaliyeNo);
    }

    /**
     * Comparison form of a waybill number: letters and digits only, upper-cased. Deliberately
     * uses the root locale rather than Turkish — a Turkish upper-case of "i" is "İ", which would
     * put a non-ASCII character in a key that PostgreSQL's own UPPER() renders as "I".
     */
    public static String toIrsaliyeKey(String raw) {
        if (raw == null) return null;
        String key = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
        return key.isEmpty() ? null : key;
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
                ", customerName=" + customerName +
                ", customerPhone=" + customerPhone +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
