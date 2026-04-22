package com.warehouse.job;

import com.warehouse.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the invoice provider for PENDING invoices and updates their
 * statuses (APPROVED / REJECTED / CANCELLED).
 *
 * <p>Logo eLogo returns PENDING immediately on SendDocument in many cases —
 * GİB processes it a few seconds to minutes later. Without this poller
 * the invoice row would stay PENDING forever unless an admin manually
 * clicked "Yeniden Oluştur".
 *
 * <p>Schedule: every 5 minutes, with a 2-minute initial delay so we don't
 * hammer the provider right after app boot.
 */
@Component
@Profile("!test")
public class InvoiceStatusPollingJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStatusPollingJob.class);

    private final InvoiceService invoiceService;

    public InvoiceStatusPollingJob(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void pollPendingInvoices() {
        try {
            int updated = invoiceService.refreshPendingStatuses();
            if (updated > 0) {
                log.info("[InvoicePolling] {} pending invoice(s) güncellendi.", updated);
            } else {
                log.debug("[InvoicePolling] PENDING fatura yok ya da güncellenen olmadı.");
            }
        } catch (Exception e) {
            log.warn("[InvoicePolling] job hata verdi: {}", e.getMessage());
        }
    }
}
