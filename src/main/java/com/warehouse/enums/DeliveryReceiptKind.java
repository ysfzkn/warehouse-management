package com.warehouse.enums;

/**
 * Which paper a receipt prints as.
 *
 * <p>Both kinds live in the same table and share the same template, because they carry the
 * same facts — who sent what to whom, and who signed for it. What differs is the moment
 * the paper is produced and therefore who signs it.</p>
 */
public enum DeliveryReceiptKind {

    /**
     * Teslimat makbuzu. Printed for a shipment whose driver and vehicle are known, in two
     * copies: the driver leaves one with the customer and brings the signed one back.
     */
    DELIVERY,

    /**
     * Depo çıkış makbuzu. Printed at the moment the goods leave the warehouse into the
     * hands of a service/carrier company, before anyone knows which driver will take them
     * onward. Single copy: it is signed on the spot by whoever collected the goods and
     * stays with us — there is no second party to leave a copy with yet.
     */
    SERVICE_HANDOVER
}
