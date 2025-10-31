package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "stock_import_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class StockImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @NotBlank
    @Column(name = "stored_filename", nullable = false, unique = true)
    private String storedFilename;

    @NotBlank
    @Column(name = "content_type", nullable = false)
    private String contentType;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"stocks", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "created_products")
    private Integer createdProducts;

    @Column(name = "updated_products")
    private Integer updatedProducts;

    @Column(name = "created_categories")
    private Integer createdCategories;

    @Column(name = "created_stocks")
    private Integer createdStocks;

    @Column(name = "updated_stocks")
    private Integer updatedStocks;

    @Column(name = "status", length = 30)
    private String status; // SUCCESS, PARTIAL, FAILED

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "failed_rows", columnDefinition = "TEXT")
    private String failedRows; // JSON formatında başarısız satır bilgileri

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


