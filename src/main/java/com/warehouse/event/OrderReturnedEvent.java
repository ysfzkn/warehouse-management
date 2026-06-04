package com.warehouse.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Event signaling that an order return has been completed.
 *
 * <p>Typical trigger: an admin moves RETURN_REQUESTED → RETURNED or REFUNDED.
 * Subscriber: {@code InvoiceCancellationListener} → direct cancellation for e-Arşiv,
 * credit note (return invoice) issuance for E-Fatura.</p>
 *
 * <p>Turkey regulations: an e-Arşiv invoice can be cancelled within 8 days; afterwards
 * a "return invoice" (credit note) must be issued. The return of an issued E-Fatura
 * document is always handled via a credit note — the original invoice is not deleted.</p>
 */
public class OrderReturnedEvent extends ApplicationEvent {

    private final Long orderId;
    private final String orderNumber;
    private final BigDecimal refundAmount;
    private final String reason;
    private final String triggeredBy;

    public OrderReturnedEvent(Object eventSource, Long orderId, String orderNumber,
                                BigDecimal refundAmount, String reason, String triggeredBy) {
        super(eventSource);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.triggeredBy = triggeredBy;
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getReason() { return reason; }
    public String getTriggeredBy() { return triggeredBy; }
}
