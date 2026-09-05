package com.warehouse.enums;

/**
 * Why goods that left the warehouse came back.
 *
 * <p>Deliberately not {@link ReturnReason}, which belongs to the storefront return flow and
 * is written from the customer's point of view — "fikrim değişti", "açıklamaya uymuyor".
 * These are the warehouse's: the shipment never completed, and the reason is about what
 * happened on the road.</p>
 */
public enum TransferReturnReason {

    /** Teslim edilemedi — adres bulunamadı, alıcıya ulaşılamadı. */
    UNDELIVERED,

    /** Müşteri teslim almadı, malı geri çevirdi. */
    REFUSED,

    /** Ürün hasarlı çıktı. */
    DAMAGED,

    /** Yanlış ürün gönderilmiş. */
    WRONG_ITEM,

    /** Sipariş edilenden fazlası gönderilmiş. */
    SURPLUS,

    OTHER
}
