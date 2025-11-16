package com.warehouse.enums;

/**
 * Describes the logical target of a stock transfer.
 */
public enum TransferType {
    /**
     * Classic warehouse-to-warehouse transfer.
     */
    WAREHOUSE,

    /**
     * Shipment leaving a warehouse towards an end customer.
     */
    CUSTOMER_DELIVERY
}

