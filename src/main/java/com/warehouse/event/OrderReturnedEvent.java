package com.warehouse.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Sipariş iadesi tamamlandı event'i.
 *
 * <p>Tipik tetikleyici: admin RETURN_REQUESTED → RETURNED ya da REFUNDED'a geçirir.
 * Abonesi: {@code InvoiceCancellationListener} → e-Arşiv için doğrudan iptal,
 * e-Fatura için credit note (iade faturası) kesimi.</p>
 *
 * <p>Türkiye mevzuatı: e-Arşiv 8 gün içinde iptal edilebilir; daha sonra
 * "iade faturası" (credit note) kesilmelidir. e-Fatura kesilmiş bir belgenin
 * iadesi her zaman credit note ile yapılır — orijinal fatura silinmez.</p>
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
