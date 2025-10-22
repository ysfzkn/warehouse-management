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
import com.warehouse.enums.TransferStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"sourceWarehouse", "destinationWarehouse", "product"})
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Source warehouse is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @NotNull(message = "Destination warehouse is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

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

    @Column(name = "transfer_date", nullable = false)
    private LocalDateTime transferDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "cancelled_date")
    private LocalDateTime cancelledDate;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
}

