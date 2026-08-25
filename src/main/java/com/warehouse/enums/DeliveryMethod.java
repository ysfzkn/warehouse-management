package com.warehouse.enums;

/**
 * How the order physically reaches the customer.
 *
 * <ul>
 *   <li>{@link #CARGO} – handed over to a cargo provider ({@code cargo_provider_id}).</li>
 *   <li>{@link #OWN_TRANSFER} – delivered by our own vehicle; tracked as a
 *       {@code CUSTOMER_DELIVERY} stock transfer linked to the order.</li>
 * </ul>
 */
public enum DeliveryMethod {
    CARGO,
    OWN_TRANSFER
}
