package com.warehouse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.warehouse.enums.TransferReturnReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Goods from a completed shipment coming back into the warehouse.
 *
 * <p>This does not undo the shipment. The goods left, that is history, and the transfer stays
 * COMPLETED — with the signed receipt still saying what went out the door. A return is a
 * separate event written on top of it: how much came back, when, and why.</p>
 *
 * <p>A shipment can have several, because a delivery that fails does so a piece at a time —
 * one item refused today, another brought back next week.</p>
 */
@Entity
@Table(name = "transfer_returns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"transfer", "items"})
public class TransferReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_transfer_id", nullable = false)
    private StockTransfer transfer;

    /** When the goods physically came back, which may be well before anyone recorded it. */
    @Column(name = "returned_at", nullable = false)
    private LocalDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferReturnReason reason;

    @Column(length = 1000)
    private String note;

    /** Sum of the line quantities, so listings need no join. */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transferReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferReturnItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.returnedAt == null) {
            this.returnedAt = this.createdAt;
        }
    }

    public void addItem(TransferReturnItem item) {
        if (item == null) return;
        item.setTransferReturn(this);
        this.items.add(item);
    }
}
