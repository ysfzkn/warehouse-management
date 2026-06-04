package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cargo shipment creation request. Provider-agnostic format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoShipmentRequest {

    /** Order number - for reference (in our system) */
    private String orderNumber;

    /** Internal order ID */
    private Long orderId;

    // --- Recipient information ---
    private String recipientName;
    private String recipientPhone;
    private String recipientEmail;

    // --- Delivery address ---
    private String recipientAddress;
    private String recipientCity;
    private String recipientDistrict;
    private String recipientPostalCode;
    /** "TR" */
    private String recipientCountryCode;

    // --- Sender (store) information ---
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String senderCity;
    private String senderDistrict;
    private String senderPostalCode;

    // --- Package information ---
    /** Package count (there may be more than one parcel) */
    private Integer packageCount;
    /** Total weight (kg) */
    private BigDecimal totalWeightKg;
    /** Total desi (volumetric weight) */
    private BigDecimal totalDesi;
    /** Package content description */
    private String contentDescription;

    /** Order amount (important for cash on delivery) */
    private BigDecimal orderAmount;

    /** Cash on delivery? */
    private boolean cashOnDelivery;
    private BigDecimal cashOnDeliveryAmount;

    // --- Cargo preference ---
    /** For Kargonomi: a specific carrier preference ("yurtici", "aras", etc.) */
    private String preferredCarrier;
    /** Service type: STANDARD, EXPRESS, NEXT_DAY, etc. */
    private String serviceType;

    /** Additional notes (to the courier) */
    private String deliveryNote;

    /** Order items (optional - some APIs require details) */
    private List<ShipmentItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentItem {
        private String productName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
