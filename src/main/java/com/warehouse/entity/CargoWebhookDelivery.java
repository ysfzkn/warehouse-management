package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Raw record of one webhook delivery, and the idempotency guard for it.
 *
 * <p>The unique constraint on {@code idempotencyKey} is the guard itself: the receiver inserts
 * before doing any work, and a constraint violation means "already handled". This replaces an
 * in-memory map that forgot everything on restart and was never shared between instances —
 * with Kargonomi retrying up to five times, duplicates were a matter of when, not if.
 *
 * <p>The stored payload doubles as the audit trail for signature and parsing problems.
 */
@Entity
@Table(name = "cargo_webhook_deliveries", indexes = {
    @Index(name = "idx_cargo_webhook_received", columnList = "received_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CargoWebhookDelivery {

    /** Claimed, not yet finished. */
    public static final String STATUS_RECEIVED = "RECEIVED";
    /** Processing outcomes. */
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String STATUS_FAILED = "FAILED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "event_type", length = 60)
    private String eventType;

    /** The provider's shipment id from the payload. */
    @Column(name = "shipment_id", length = 100)
    private String shipmentId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    /** Which retry this was, as reported by the sender. */
    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
    }
}
