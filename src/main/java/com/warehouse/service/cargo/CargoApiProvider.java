package com.warehouse.service.cargo;

/**
 * Cargo API provider interface.
 *
 * The various cargo integrations in Turkey implement this interface:
 * - Kargonomi (multi-carrier aggregator)
 * - Yurtiçi Kargo (direct)
 * - Aras Kargo (direct)
 * - MNG (direct)
 *
 * Configuration is read from the site_settings table.
 */
public interface CargoApiProvider {

    /**
     * Provider name. E.g. "KARGONOMI", "YURTICI", "ARAS", "MOCK".
     */
    String getProviderName();

    /**
     * Is this provider active and are its credentials configured?
     */
    boolean isEnabled();

    /**
     * Creates a cargo shipment.
     * Obtains a tracking number from the carrier.
     */
    CargoShipmentResult createShipment(CargoShipmentRequest request);

    /**
     * Queries the cargo status by tracking number.
     */
    CargoTrackingStatus getTrackingStatus(String trackingNumber);

    /**
     * Cancels the shipment.
     */
    CargoShipmentResult cancelShipment(String providerShipmentId);

    /**
     * Creates a return label (for the customer to return the product).
     */
    CargoShipmentResult createReturnShipment(CargoShipmentRequest request);
}
