package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "stock_transfer_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"transfer", "product", "photo"})
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Transfer reference is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_transfer_id", nullable = false)
    private StockTransfer transfer;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "stock_id")
    private Long stockId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

    @OneToOne(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private StockTransferItemPhoto photo;

    public void setPhoto(StockTransferItemPhoto photo) {
        if (photo != null) {
            photo.setItem(this);
        }
        this.photo = photo;
    }
}


