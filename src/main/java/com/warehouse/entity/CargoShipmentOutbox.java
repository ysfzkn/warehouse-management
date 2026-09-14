package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An order whose cargo shipment could not be created, waiting for another attempt.
 *
 * <p>One row per order (the order id is unique): a shipment is either owed or it is not.
 * The retry job picks up rows whose {@code nextAttemptAt} has passed, and gives up after
 * {@link #MAX_ATTEMPTS}, at which point a human is told rather than the order quietly
 * never shipping.
 */
@Entity
@Table(name = "cargo_shipment_outbox", indexes = {
    @Index(name = "idx_cargo_outbox_due", columnList = "status, next_attempt_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CargoShipmentOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_ABANDONED = "ABANDONED";

    /** After this many failures the order needs a person, not another retry. */
    public static final int MAX_ATTEMPTS = 5;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Backoff between attempts: 5, 15, 45, 135 minutes. Long enough that a carrier outage
     * is over by the next try, short enough that a same-day shipment is still same-day.
     */
    public LocalDateTime backoffFrom(LocalDateTime now) {
        long minutes = (long) (5 * Math.pow(3, Math.max(0, attempts - 1)));
        return now.plusMinutes(Math.min(minutes, 240));
    }
}
