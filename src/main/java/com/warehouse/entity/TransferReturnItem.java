package com.warehouse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One shipped line coming back, in whole or in part.
 *
 * <p>Points at the {@link StockTransferItem} rather than at a product, because that is what
 * bounds it: a line can never have more returned against it than went out on it, and the
 * stock row the goods came off is the one they go back onto.</p>
 */
@Entity
@Table(name = "transfer_return_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"transferReturn", "transferItem"})
public class TransferReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_return_id", nullable = false)
    private TransferReturn transferReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_transfer_item_id", nullable = false)
    private StockTransferItem transferItem;

    /** Denormalised so the return history reads without walking back through the shipment. */
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;
}
