package com.warehouse.event;

import org.springframework.context.ApplicationEvent;

/**
 * Sipariş başarıyla ödendi event'i.
 *
 * <p>Aboneler:
 * <ul>
 *   <li>{@code InvoiceAutoCreateListener} — fatura otomatik kesimi (@Async)</li>
 *   <li>Gelecekte: kargo otomatik gönderi, müşteri SMS, marketplace stok sync vb.</li>
 * </ul>
 *
 * <p><b>Önemli:</b> {@code AFTER_COMMIT} phase'inde dinleyiciler tetiklenmeli
 * (sipariş gerçekten DB'ye yazıldıktan sonra). {@code @TransactionalEventListener}
 * kullanın.</p>
 */
public class OrderPaidEvent extends ApplicationEvent {

    private final Long orderId;
    private final String orderNumber;
    private final String triggeredBy; // "iyzico", "bank_transfer", "manual", vb.

    public OrderPaidEvent(Object eventSource, Long orderId, String orderNumber, String triggeredBy) {
        super(eventSource);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.triggeredBy = triggeredBy;
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public String getTriggeredBy() { return triggeredBy; }
}
