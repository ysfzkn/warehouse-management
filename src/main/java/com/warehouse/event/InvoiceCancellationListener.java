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
 * Sipariş iade event'i için fatura iptal/credit note akışı.
 *
 * <p><b>Türkiye e-fatura iade kuralları:</b>
 * <ul>
 *   <li><b>e-Arşiv (bireysel):</b> Kesildikten sonra <b>8 gün içinde</b> iptal
 *       edilebilir ({@code CANCELEARCHIVEINVOICE}). 8 gün sonrasında iptal değil,
 *       <b>iade faturası</b> kesmek gerekir.</li>
 *   <li><b>e-Fatura (tüzel):</b> Kesilmiş bir e-faturanın iptal edilmesi mümkün
 *       değildir. Mutlaka <b>credit note</b> (red/iade faturası) kesilmelidir.</li>
 * </ul></p>
 *
 * <p><b>MVP davranışı:</b> Bu listener iptal eylemini admin'e bildirir ve
 * uygunsa otomatik iptal eder. Credit note akışı şu an "manuel onay gerekli"
 * mantığıyla ERROR statüsünde kayıt oluşturur (admin tamamlar). Tam credit
 * note kesimi UblTrInvoiceBuilder'da ProfileID="IADE" + creditedInvoiceID alanı
 * gerektirir ve Logo SendDocument farklı parametreler ister — gelecek
 * iterasyonda tam otomatize edilecek.</p>
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
                // e-Arşiv + 8 gün içinde → doğrudan iptal et (CANCELEARCHIVEINVOICE)
                log.info("[Invoice] e-Arşiv {} gün içinde — iptal ediliyor (order={})",
                        EARSIV_CANCEL_WINDOW_DAYS, event.getOrderNumber());
                invoiceService.cancelInvoice(inv.getId());
            } else {
                // e-Fatura veya 8 gün geçmiş e-Arşiv → credit note (iade faturası) kes
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
                    // Credit note kesimi başarısız → admin digest (ERROR statüsü) yakalar.
                    // Manuel müdahale için admin /api/admin/invoices/{id}/credit-note kullanabilir.
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
