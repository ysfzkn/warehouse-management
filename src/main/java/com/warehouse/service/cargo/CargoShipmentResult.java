package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of creating a cargo shipment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoShipmentResult {

    private boolean success;

    /** Cargo company tracking number (to be shown to the customer) */
    private String trackingNumber;

    /** Tracking URL on the cargo company's website */
    private String trackingUrl;

    /** Cargo company name (Yurtiçi, Aras, MNG, etc.) */
    private String carrierName;

    /** Cargo company code (yurtici, aras, mng, etc.) */
    private String carrierCode;

    /** Label PDF URL (for printing) */
    private String labelUrl;

    /** Provider's system ID (for cancellation / lookup) */
    private String providerShipmentId;

    /** On error */
    private String errorCode;
    private String errorMessage;

    public static CargoShipmentResult success(String trackingNumber, String trackingUrl,
                                               String carrierName, String providerId) {
        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .trackingUrl(trackingUrl)
                .carrierName(carrierName)
                .providerShipmentId(providerId)
                .build();
    }

    public static CargoShipmentResult failure(String errorCode, String errorMessage) {
        return CargoShipmentResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
