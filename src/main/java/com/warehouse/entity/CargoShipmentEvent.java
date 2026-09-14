package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One movement of a shipment, as reported by the carrier.
 *
 * <p>Both the webhook and the polling job append here, so an order's cargo history survives
 * even when the carrier later overwrites its own status. Rows are deduplicated on
 * (order, status code, occurred_at) — the same movement arriving twice is not a new event.
 */
@Entity
@Table(name = "cargo_shipment_events", indexes = {
    @Index(name = "idx_cargo_events_order", columnList = "order_id, occurred_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CargoShipmentEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Denormalised so the ledger stays readable without a join. */
    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    /** Raw carrier status code, e.g. "webservice_shipment_started". */
    @Column(name = "status_code", length = 60)
    private String statusCode;

    /** Human-readable label for that code. */
    @Column(name = "status_label", length = 120)
    private String statusLabel;

    /** Our provider-agnostic status, i.e. {@code CargoTrackingStatus.CargoStatus}. */
    @Column(name = "mapped_status", length = 30)
    private String mappedStatus;

    @Column(length = 500)
    private String description;

    @Column(length = 200)
    private String location;

    /** When the carrier says it happened. Null for status changes with no timestamp. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    /** KARGONOMI_WEBHOOK | CARGO_TRACKING_JOB */
    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
