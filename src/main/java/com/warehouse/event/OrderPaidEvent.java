package com.warehouse.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired when an order has been paid successfully.
 *
 * <p>Subscribers:
 * <ul>
 *   <li>{@code InvoiceAutoCreateListener} — automatic invoice issuance (@Async)</li>
 *   <li>Future: automatic shipment dispatch, customer SMS, marketplace stock sync, etc.</li>
 * </ul>
 *
 * <p><b>Important:</b> listeners must be triggered in the {@code AFTER_COMMIT} phase
 * (after the order is actually written to the DB). Use
 * {@code @TransactionalEventListener}.</p>
 */
public class OrderPaidEvent extends ApplicationEvent {

    private final Long orderId;
    private final String orderNumber;
    private final String triggeredBy; // "iyzico", "bank_transfer", "manual", etc.

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
