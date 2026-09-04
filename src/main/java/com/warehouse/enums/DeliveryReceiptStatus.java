package com.warehouse.enums;

/**
 * Lifecycle of a delivery receipt.
 *
 * <p>Deliberately short. Whether the signed paper copy has come back is <em>not</em> a
 * status — it is derived from the attachment list, so the two facts ("we confirmed the
 * delivery" and "we have the signed page on file") stay independent. A delivery can be
 * confirmed by phone before the driver returns with the paperwork, and the receipt can
 * be photographed before anyone gets round to confirming it in the panel.</p>
 */
public enum DeliveryReceiptStatus {

    /** Printed and handed to the driver; the goods are on their way. */
    ISSUED,

    /** Someone recorded who took delivery and when. */
    DELIVERED,

    /** The shipment was cancelled after the receipt had been printed. */
    CANCELLED
}
