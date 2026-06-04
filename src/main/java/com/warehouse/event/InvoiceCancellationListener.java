package com.warehouse.event;

import com.warehouse.dto.InvoiceDto;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import com.warehouse.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Invoice cancellation / credit note flow for the order return event.
 *
 * <p><b>Turkey e-invoice return rules:</b>
 * <ul>
 *   <li><b>e-Arşiv (individual):</b> Can be cancelled <b>within 8 days</b> of
 *       being issued ({@code CANCELEARCHIVEINVOICE}). After 8 days it cannot be
 *       cancelled; a <b>credit (return) invoice</b> must be issued instead.</li>
 *   <li><b>e-Fatura (corporate):</b> An issued e-invoice cannot be cancelled.
 *       A <b>credit note</b> (rejection/return invoice) must always be issued.</li>
 * </ul></p>
 *
 * <p><b>MVP behavior:</b> This listener notifies the admin of the cancellation
 * action and cancels automatically when eligible. The credit note flow currently
 * creates a record in ERROR status with a "manual approval required" logic (the
 * admin completes it). Full credit note issuance requires ProfileID="IADE" plus a
 * creditedInvoiceID field in UblTrInvoiceBuilder, and Logo SendDocument expects
 * different parameters — this will be fully automated in a future iteration.</p>
 */
@Component
public class InvoiceCancellationListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCancellationListener.class);
    private static final int EARSIV_CANCEL_WINDOW_DAYS = 8;

    private final InvoiceService invoiceService;

    public InvoiceCancellationListener(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReturned(OrderReturnedEvent event) {
        try {
            Optional<InvoiceDto> existing = invoiceService.getInvoiceByOrderId(event.getOrderId());
            if (existing.isEmpty()) {
                log.info("[Invoice] İade event'i alındı ama sipariş için fatura yok — atlandı: order={}",
                        event.getOrderNumber());
                return;
            }
            InvoiceDto inv = existing.get();
            if (inv.getStatus() == InvoiceStatus.CANCELLED) {
                log.info("[Invoice] Fatura zaten iptal: invoiceId={}, order={}",
                        inv.getId(), event.getOrderNumber());
                return;
            }
            if (inv.getStatus() != InvoiceStatus.APPROVED) {
                log.warn("[Invoice] İade için fatura status={}; APPROVED olmadığından iptal/credit note işlenmedi (order={})",
                        inv.getStatus(), event.getOrderNumber());
                return;
            }

            boolean eArsivWithinWindow = inv.getInvoiceType() == InvoiceType.E_ARSIV
                    && inv.getIssuedAt() != null
                    && Duration.between(inv.getIssuedAt(), LocalDateTime.now()).toDays() < EARSIV_CANCEL_WINDOW_DAYS;

            if (eArsivWithinWindow) {
                // e-Arşiv + within 8 days → cancel directly (CANCELEARCHIVEINVOICE)
                log.info("[Invoice] e-Arşiv {} gün içinde — iptal ediliyor (order={})",
                        EARSIV_CANCEL_WINDOW_DAYS, event.getOrderNumber());
                invoiceService.cancelInvoice(inv.getId());
            } else {
                // e-Fatura or e-Arşiv past 8 days → issue a credit note (return invoice)
                log.info("[Invoice] İade faturası kesiliyor (otomatik): invoiceId={}, type={}, order={}",
                        inv.getId(), inv.getInvoiceType(), event.getOrderNumber());
                try {
                    var creditNote = invoiceService.createCreditNote(
                            inv.getId(),
                            event.getRefundAmount(),
                            event.getReason() != null ? event.getReason() : "Sipariş iadesi");
                    log.info("[Invoice] Credit note oluşturuldu: id={}, no={}, status={}",
                            creditNote.getId(), creditNote.getInvoiceNumber(), creditNote.getStatus());
                } catch (Exception ce) {
                    // Credit note issuance failed → the admin digest (ERROR status) catches it.
                    // For manual intervention the admin can use /api/admin/invoices/{id}/credit-note.
                    log.error("[Invoice] Credit note kesimi başarısız: invoiceId={}, order={}, hata={}",
                            inv.getId(), event.getOrderNumber(), ce.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Invoice] İade fatura işlemi başarısız (order={}): {}",
                    event.getOrderNumber(), e.getMessage());
        }
    }
}
