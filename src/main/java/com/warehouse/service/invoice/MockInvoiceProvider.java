package com.warehouse.service.invoice;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.repository.InvoiceRepository;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock e-invoice provider.
 * Used for development/testing until a real provider is integrated.
 * Generates an invoice number and returns the APPROVED status.
 *
 * <p><b>Production Guard:</b> with the {@code invoice.mock-enabled=false} property the bean
 * is not created at all (it is absent from the Spring context). The default is {@code true}
 * — automatically active in dev/test environments. In application-prod.properties
 * {@code invoice.mock-enabled=false} is set. This makes it impossible to accidentally
 * issue a MOCK invoice in prod.</p>
 *
 * <p>Additionally, via {@link PostConstruct}, a loud warning is emitted if it becomes
 * active under the prod profile (so it is not overlooked).</p>
 */
@Component
@ConditionalOnProperty(name = "invoice.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockInvoiceProvider implements InvoiceProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockInvoiceProvider.class);
    private static final DateTimeFormatter INVOICE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong sequence = new AtomicLong(1);

    private final SiteSettingService settingService;
    private final InvoiceRepository invoiceRepository;
    private final Environment environment;

    public MockInvoiceProvider(SiteSettingService settingService,
                               InvoiceRepository invoiceRepository,
                               Environment environment) {
        this.settingService = settingService;
        this.invoiceRepository = invoiceRepository;
        this.environment = environment;
    }

    /**
     * If MockInvoiceProvider becomes accidentally active under the production profile,
     * emit a loud warning at startup — so the operator notices immediately.
     * @ConditionalOnProperty should already prevent the bean, but this is an
     * extra warning for defense-in-depth.
     */
    @PostConstruct
    void warnIfProd() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = false;
        for (String p : activeProfiles) if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) { isProd = true; break; }
        if (isProd) {
            logger.error("╔══════════════════════════════════════════════════════════════════╗");
            logger.error("║  🚨 MockInvoiceProvider AKTIF — PRODUCTION ORTAMI!              ║");
            logger.error("║  Gerçek müşterilere SAHTE fatura kesilebilir. Yasal risk var.    ║");
            logger.error("║  application-prod.properties'te invoice.mock-enabled=false       ║");
            logger.error("║  ayarlandığından emin olun ve gerçek bir provider'ı aktive edin. ║");
            logger.error("╚══════════════════════════════════════════════════════════════════╝");
        } else {
            logger.info("MockInvoiceProvider hazır (profil: {}). Production'da disable edilmelidir.", String.join(",", activeProfiles));
        }
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public boolean isEnabled() {
        String provider = settingService.getSetting("invoice_provider");
        // MOCK is active when no provider is set OR explicitly set to MOCK
        return provider == null || provider.isBlank() || "MOCK".equalsIgnoreCase(provider);
    }

    @Override
    public InvoiceResult createInvoice(Invoice invoice, Order order, List<OrderItem> items) {
        String datePrefix = LocalDateTime.now().format(INVOICE_NUMBER_FORMAT);
        String invoiceNumber = "INV" + datePrefix + String.format("%06d", sequence.getAndIncrement());
        String providerInvoiceId = "MOCK-" + invoiceNumber;

        logger.info("Mock e-fatura oluşturuldu: {} (sipariş: {})",
                invoiceNumber, invoice.getOrder().getOrderNumber());

        return InvoiceResult.builder()
                .success(true)
                .invoiceNumber(invoiceNumber)
                .providerInvoiceId(providerInvoiceId)
                .status(InvoiceStatus.APPROVED)
                .gibResponse("Mock GİB onayı - Test ortamı")
                .build();
    }

    @Override
    public InvoiceResult queryStatus(String providerInvoiceId) {
        logger.info("Mock fatura durumu sorgulandı: {}", providerInvoiceId);
        return InvoiceResult.builder()
                .success(true)
                .providerInvoiceId(providerInvoiceId)
                .status(InvoiceStatus.APPROVED)
                .gibResponse("Mock GİB durumu - Onaylı")
                .build();
    }

    @Override
    public InvoiceResult cancelInvoice(String providerInvoiceId) {
        logger.info("Mock fatura iptal edildi: {}", providerInvoiceId);
        return InvoiceResult.builder()
                .success(true)
                .providerInvoiceId(providerInvoiceId)
                .status(InvoiceStatus.CANCELLED)
                .gibResponse("Mock GİB iptali - Test ortamı")
                .build();
    }

    @Override
    public byte[] downloadPdf(String providerInvoiceId) {
        logger.info("Mock fatura PDF indiriliyor: {}", providerInvoiceId);

        // providerInvoiceId → fetch the invoice details and generate a real PDF
        Invoice invoice = invoiceRepository.findAll().stream()
                .filter(i -> providerInvoiceId.equals(i.getProviderInvoiceId()))
                .findFirst()
                .orElse(null);

        return renderMockPdf(invoice, providerInvoiceId);
    }

    /** Generate a simple but valid PDF — the customer can open it and see the invoice details. */
    private byte[] renderMockPdf(Invoice invoice, String providerInvoiceId) {
        SimplePdfBuilder pdf = new SimplePdfBuilder();
        int y = 800;

        pdf.line(50, y, "TEST FATURASI (MOCK)", 20);
        y -= 30;
        pdf.line(50, y, "Bu fatura test amaclidir — gercek GIB onayli degildir.", 9);
        y -= 30;

        if (invoice != null) {
            pdf.line(50, y, "Fatura No: " + safe(invoice.getInvoiceNumber()), 13);
            y -= 22;
            pdf.line(50, y, "Tip: " + (invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : "-"));
            y -= 18;
            pdf.line(50, y, "Durum: " + (invoice.getStatus() != null ? invoice.getStatus().name() : "-"));
            y -= 18;
            if (invoice.getIssuedAt() != null) {
                pdf.line(50, y, "Duzenlenme: " + invoice.getIssuedAt().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                y -= 18;
            }
            y -= 12;

            pdf.line(50, y, "ALICI", 13);
            y -= 20;
            pdf.line(50, y, safe(invoice.getRecipientName()));
            y -= 16;
            if (invoice.getRecipientTaxId() != null) {
                pdf.line(50, y, "Vergi/TC No: " + invoice.getRecipientTaxId());
                y -= 16;
            }
            if (invoice.getRecipientAddress() != null) {
                pdf.line(50, y, safe(truncate(invoice.getRecipientAddress(), 90)));
                y -= 16;
            }
            y -= 10;

            pdf.line(50, y, "TUTARLAR", 13);
            y -= 20;
            pdf.line(50, y, "Ara Toplam: " + fmtMoney(invoice.getSubtotal()));
            y -= 16;
            pdf.line(50, y, "KDV: " + fmtMoney(invoice.getVatAmount()));
            y -= 16;
            pdf.line(50, y, "TOPLAM: " + fmtMoney(invoice.getTotalAmount()), 14);
            y -= 24;

            if (invoice.getEttn() != null) {
                pdf.line(50, y, "ETTN: " + invoice.getEttn(), 8);
                y -= 14;
            }
        } else {
            pdf.line(50, y, "Provider ID: " + providerInvoiceId);
            y -= 20;
            pdf.line(50, y, "Fatura detaylari bulunamadi.");
        }

        pdf.line(50, 40, "— TEST ORTAMI — GERCEK FATURA ICIN LOGO eLOGO SAGLAYICISINA GECIN —", 8);
        return pdf.build();
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String truncate(String s, int max) { return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "..."); }
    private String fmtMoney(BigDecimal v) {
        if (v == null) return "0,00 TL";
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " TL";
    }
}
