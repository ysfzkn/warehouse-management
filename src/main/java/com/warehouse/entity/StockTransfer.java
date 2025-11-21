package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfers")
@NamedEntityGraph(
        name = StockTransfer.GRAPH_WITH_RELATIONS,
        attributeNodes = {
                @NamedAttributeNode("sourceWarehouse"),
                @NamedAttributeNode("destinationWarehouse"),
                @NamedAttributeNode("product"),
                @NamedAttributeNode(value = "items", subgraph = "StockTransfer.items")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "StockTransfer.items",
                        attributeNodes = {
                                @NamedAttributeNode("product")
                        }
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"sourceWarehouse", "destinationWarehouse", "product", "items"})
public class StockTransfer {

    public static final String GRAPH_WITH_RELATIONS = "StockTransfer.with-relations";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Source warehouse is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "product_id")
    private Product product;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"transfer", "hibernateLazyInitializer", "handler"})
    private List<StockTransferItem> items = new ArrayList<>();

    @NotBlank(message = "Driver name is required")
    @Size(min = 3, max = 100, message = "Driver name must be between 3 and 100 characters")
    @Column(name = "driver_name", nullable = false, length = 100)
    private String driverName;

    @NotBlank(message = "Driver TC ID is required")
    @Pattern(regexp = "^[0-9]{11}$", message = "Driver TC ID must be 11 digits")
    @Column(name = "driver_tc_id", nullable = false, length = 11)
    private String driverTcId;

    @NotBlank(message = "Driver phone is required")
    @Size(min = 10, max = 20, message = "Driver phone must be between 10 and 20 characters")
    @Column(name = "driver_phone", nullable = false, length = 20)
    private String driverPhone;

    @NotBlank(message = "Vehicle plate is required")
    @Size(min = 2, max = 20, message = "Vehicle plate must be between 2 and 20 characters")
    @Column(name = "vehicle_plate", nullable = false, length = 20)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 40)
    private TransferType transferType = TransferType.WAREHOUSE;

    @Size(max = 150, message = "Customer full name cannot exceed 150 characters")
    @Column(name = "customer_full_name", length = 150)
    private String customerFullName;

    @Size(max = 20, message = "Customer phone cannot exceed 20 characters")
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Size(max = 500, message = "Customer address cannot exceed 500 characters")
    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Europe/Istanbul")
    @Column(name = "transfer_date", nullable = false)
    private LocalDateTime transferDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Europe/Istanbul")
    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Europe/Istanbul")
    @Column(name = "cancelled_date")
    private LocalDateTime cancelledDate;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Column(length = 500)
    private String notes;

    @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Size(max = 100, message = "Created by cannot exceed 100 characters")
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Size(max = 500, message = "Completion note cannot exceed 500 characters")
    @Column(name = "completion_note", length = 500)
    private String completionNote;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Europe/Istanbul")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Europe/Istanbul")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.transferDate == null) {
            this.transferDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setItems(List<StockTransferItem> items) {
        this.items.clear();
        if (items != null) {
            for (StockTransferItem item : items) {
                addItem(item);
            }
        }
    }

    public void addItem(StockTransferItem item) {
        if (item == null) {
            return;
        }
        item.setTransfer(this);
        this.items.add(item);
    }
}

