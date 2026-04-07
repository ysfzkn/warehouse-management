package com.warehouse.entity;

import com.warehouse.enums.StockEventType;
import com.warehouse.enums.StockEventSource;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_events", indexes = {
    @Index(name = "idx_stock_events_stock", columnList = "stock_id"),
    @Index(name = "idx_stock_events_product", columnList = "product_id"),
    @Index(name = "idx_stock_events_type", columnList = "event_type"),
    @Index(name = "idx_stock_events_created", columnList = "created_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class StockEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private StockEventType eventType;

    @Column(name = "old_value")
    private Integer oldValue;

    @Column(name = "new_value")
    private Integer newValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockEventSource source = StockEventSource.SYSTEM;

    @Column(name = "source_detail", length = 200)
    private String sourceDetail;

    /**
     * Order number that caused this event (denormalised on purpose so the audit
     * history survives if the order is later deleted). Nullable — only set for
     * events originating from an order (checkout reservation, delivery, cancel).
     */
    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
