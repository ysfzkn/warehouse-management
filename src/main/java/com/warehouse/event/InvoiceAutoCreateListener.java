package com.warehouse.event;

import com.warehouse.service.InvoiceService;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Automatic invoice issuance when an order is paid.
 *
 * <p><b>Architectural decisions:</b>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — The invoice is
 *       not issued before the order is persisted to the DB (race condition guard).</li>
 *   <li>{@code @Async} — The Logo SOAP call can take 2-10 seconds; the order
 *       confirmation endpoint should not wait for it. On failure the invoice stays
 *       in DRAFT/ERROR status and is reported via the admin digest.</li>
 *   <li>Feature flag: if the {@code invoice_auto_generate} site setting is disabled,
 *       return silently (the user can issue it manually from the admin panel).</li>
 *   <li>Idempotency: {@link InvoiceService#createInvoiceForOrder} checks for an
 *       existing invoice (it will not recreate one unless it is ERROR/CANCELLED).</li>
 * </ul>
 */
@Component
public class InvoiceAutoCreateListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAutoCreateListener.class);

    private final InvoiceService invoiceService;
    private final SiteSettingService settingService;

    public InvoiceAutoCreateListener(InvoiceService invoiceService, SiteSettingService settingService) {
        this.invoiceService = invoiceService;
        this.settingService = settingService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        String autoGenerate = settingService.getSetting("invoice_auto_generate");
        if (autoGenerate == null || !"true".equalsIgnoreCase(autoGenerate.trim())) {
            log.debug("[Invoice] Auto-generate kapalı, atlandı: order={}", event.getOrderNumber());
            return;
        }
        try {
            log.info("[Invoice] Otomatik fatura kesimi tetiklendi: order={}, trigger={}",
                    event.getOrderNumber(), event.getTriggeredBy());
            invoiceService.createInvoiceForOrder(event.getOrderId());
        } catch (Exception e) {
            // Catch errors coming from Logo to prevent the event from propagating —
            // the invoice was recorded in ERROR status; it will be reported via the admin digest.
            log.error("[Invoice] Otomatik fatura kesimi başarısız: order={}, hata={}",
                    event.getOrderNumber(), e.getMessage());
        }
    }
}
