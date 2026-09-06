package com.warehouse.enums;

/**
 * What happens to the linked order when its shipment comes back.
 *
 * <p>Two decisions hide in one return, and they are genuinely independent: where the goods
 * go (always back on the shelf) and whether the order is still alive. A failed delivery
 * attempt and a dead order look identical in the warehouse and completely different in the
 * order book, so the person recording the return has to say which one it was.</p>
 *
 * <p>The choice also decides the reservation. Stock reserved for an order is consumed when
 * the shipment completes; if the order lives on, those units have to go back to being
 * earmarked for it, or the next customer can buy goods that are already spoken for.</p>
 */
public enum TransferReturnOrderOutcome {

    /**
     * Teslimat denemesi başarısız, sipariş açık kalıyor — yeniden gönderilecek.
     *
     * <p>The order keeps its status and the returned units are reserved for it again.</p>
     */
    KEEP_ORDER,

    /**
     * Sipariş iade edildi.
     *
     * <p>The order walks to {@code RETURNED} through the state machine, and the units come
     * back unreserved because nothing is waiting on them any more. Refunding is the next,
     * separate step — {@code RETURNED → REFUNDED} is where the money is handled.</p>
     */
    RETURN_ORDER
}
