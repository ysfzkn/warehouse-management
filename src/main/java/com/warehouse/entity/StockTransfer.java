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
import com.warehouse.enums.TransferApprovalStatus;
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
    @Column(name = "approval_status", length = 20, nullable = false)
    private TransferApprovalStatus approvalStatus = TransferApprovalStatus.NONE;

    @Column(name = "approval_requested_by", length = 100)
    private String approvalRequestedBy;

    @Column(name = "approval_requested_at")
    private LocalDateTime approvalRequestedAt;

    @Column(name = "approval_decision_by", length = 100)
    private String approvalDecisionBy;

    @Column(name = "approval_decision_at")
    private LocalDateTime approvalDecisionAt;

    @Size(max = 500, message = "Approval note cannot exceed 500 characters")
    @Column(name = "approval_note", length = 500)
    private String approvalNote;

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

    /**
     * The order this shipment fulfils, when the customer chose delivery by our own
     * vehicle instead of a cargo provider. Only set for {@link TransferType#CUSTOMER_DELIVERY}.
     * The order number is denormalised so listings can render it without a join.
     */
    @Column(name = "order_id")
    private Long orderId;

    /**
     * The storefront customer this delivery goes to, when the recipient happens to have an
     * e-commerce account. Optional and matchable after the fact — walk-in recipients simply
     * stay as the free-text {@link #customerFullName} / {@link #customerPhone} fields.
     */
    @Column(name = "customer_id")
    private Long customerId;

    @Size(max = 50)
    @Column(name = "order_number", length = 50)
    private String orderNumber;

    /**
     * Customer name + phone with Turkish letters folded onto ASCII, so a search for "Ballı"
     * finds a record typed "Balli". Maintained by the service on every write.
     */
    @Size(max = 400)
    @Column(name = "customer_search", length = 400)
    private String customerSearch;

    /** Same treatment for driver name, phone, TC and plate. */
    @Size(max = 400)
    @Column(name = "driver_search", length = 400)
    private String driverSearch;

    /**
     * The directory entry this transfer's driver belongs to. The name/TC/phone/plate above stay
     * as they were recorded — merging duplicate drivers moves this link, never the history.
     */
    @Column(name = "driver_id")
    private Long driverId;

    /**
     * The vehicle directory entry used for this transfer. As with the driver, the plate text
     * above stays as recorded — this only says which vehicle record it refers to.
     */
    @Column(name = "vehicle_id")
    private Long vehicleId;

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

    @Column(name = "delete_request", nullable = false)
    private boolean deleteRequest = false;

    /**
     * Derived here rather than in a service so no write path can forget: an out-of-date search
     * column silently drops the record out of the customer / driver filters.
     */
    private void refreshSearchColumns() {
        this.customerSearch = com.warehouse.util.TurkishText.normalizeForSearch(
                this.customerFullName, this.customerPhone);
        this.driverSearch = com.warehouse.util.TurkishText.normalizeForSearch(
                this.driverName, this.driverPhone, this.driverTcId, this.vehiclePlate);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        refreshSearchColumns();
        if (this.transferDate == null) {
            this.transferDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        refreshSearchColumns();
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

