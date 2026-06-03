package com.warehouse.job;

import com.warehouse.entity.Invoice;
import com.warehouse.repository.InvoiceRepository;
import com.warehouse.service.EmailService;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends a daily e-Fatura status digest to the admin every morning at 08:00.
 *
 * <p>Digest contents:
 * <ul>
 *   <li>Invoices that became ERROR / REJECTED within the last 24 hours</li>
 *   <li>Invoices stuck in PENDING for more than 24 hours</li>
 * </ul>
 *
 * <p>If there are no issues, no mail is sent (zero-noise digest).
 *
 * <p>Recipient: the {@code invoice_admin_digest_email} site_setting; if blank,
 * {@code contact_form_email} is used.
 */
@Component
@Profile("!test")
public class InvoiceAdminDigestJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAdminDigestJob.class);

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final SiteSettingService settings;

    public InvoiceAdminDigestJob(InvoiceRepository invoiceRepository,
                                  EmailService emailService,
                                  SiteSettingService settings) {
        this.invoiceRepository = invoiceRepository;
        this.emailService = emailService;
        this.settings = settings;
    }

    /**
     * Every day at 08:00 Turkey time (Europe/Istanbul).
     * Cron: second minute hour day month day-of-week
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Istanbul")
    @SchedulerLock(name = "invoiceAdminDigest", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void sendDailyDigest() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last24h = now.minusHours(24);

            List<Invoice> errors = invoiceRepository.findRecentErrors(last24h);
            List<Invoice> stuck = invoiceRepository.findStuckPending(last24h);

            if (errors.isEmpty() && stuck.isEmpty()) {
                log.debug("[InvoiceDigest] Sorun yok, mail gönderilmiyor.");
                return;
            }

            String adminEmail = resolveAdminEmail();
            if (adminEmail == null) {
                log.warn("[InvoiceDigest] {} hata + {} stuck fatura var ama alıcı e-posta yapılandırılmamış "
                        + "(invoice_admin_digest_email veya contact_form_email).",
                        errors.size(), stuck.size());
                return;
            }

            emailService.sendAdminInvoiceDigest(adminEmail, toRows(errors), toRows(stuck));
            log.info("[InvoiceDigest] mail gönderildi → {} (hata={}, stuck={})",
                    adminEmail, errors.size(), stuck.size());
        } catch (Exception e) {
            log.error("[InvoiceDigest] job hata verdi: {}", e.getMessage(), e);
        }
    }

    private String resolveAdminEmail() {
        String dedicated = settings.getSetting("invoice_admin_digest_email");
        if (dedicated != null && !dedicated.isBlank()) return dedicated.trim();
        String contact = settings.getSetting("contact_form_email");
        if (contact != null && !contact.isBlank()) return contact.trim();
        return null;
    }

    private List<Map<String, Object>> toRows(List<Invoice> invoices) {
        List<Map<String, Object>> rows = new ArrayList<>(invoices.size());
        for (Invoice i : invoices) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("invoiceNumber", i.getInvoiceNumber() != null ? i.getInvoiceNumber() : "(numara yok)");
            r.put("orderNumber", i.getOrder() != null ? i.getOrder().getOrderNumber() : "-");
            r.put("recipientName", i.getRecipientName() != null ? i.getRecipientName() : "-");
            r.put("totalAmount", i.getTotalAmount() != null ? i.getTotalAmount().toPlainString() + " TL" : "-");
            r.put("errorMessage", i.getErrorMessage() != null ? i.getErrorMessage()
                    : (i.getStatus() != null ? i.getStatus().name() : "-"));
            rows.add(r);
        }
        return rows;
    }
}
