package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cargo tracking status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoTrackingStatus {

    /** Tracking number */
    private String trackingNumber;

    /** Current status code */
    private CargoStatus status;

    /** Raw status text (as received from the provider) */
    private String statusText;

    /** Estimated delivery date */
    private LocalDateTime estimatedDelivery;

    /** Actual delivery date (if DELIVERED) */
    private LocalDateTime deliveredAt;

    /** Person who received the delivery (if any) */
    private String deliveredTo;

    /** Historical events */
    private List<TrackingEvent> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEvent {
        private LocalDateTime timestamp;
        private String description;
        private String location;
        private CargoStatus status;
    }

    public enum CargoStatus {
        /** Shipment created, not yet picked up */
        CREATED,
        /** Handed over to the cargo company */
        PICKED_UP,
        /** In transit / on the way */
        IN_TRANSIT,
        /** At the distribution branch */
        OUT_FOR_DELIVERY,
        /** Delivered */
        DELIVERED,
        /** Return in transit */
        RETURN_IN_TRANSIT,
        /** Return completed */
        RETURNED,
        /** Cancelled */
        CANCELLED,
        /** Problematic (could not be delivered, wrong address, etc.) */
        FAILED,
        /** Unknown / unmapped status */
        UNKNOWN
    }
}
