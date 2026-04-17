package com.warehouse.service.invoice;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock e-fatura sağlayıcısı.
 * Gerçek bir sağlayıcı entegre edilene kadar geliştirme/test amaçlı kullanılır.
 * Fatura numarası üretir ve APPROVED durumu döner.
 */
@Component
public class MockInvoiceProvider implements InvoiceProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockInvoiceProvider.class);
    private static final DateTimeFormatter INVOICE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong sequence = new AtomicLong(1);

    private final SiteSettingService settingService;

    public MockInvoiceProvider(SiteSettingService settingService) {
        this.settingService = settingService;
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
        // Mock PDF döndür - gerçek implementasyonda sağlayıcıdan indirilir
        String mockPdfContent = "Mock PDF - Fatura: " + providerInvoiceId;
        return mockPdfContent.getBytes();
    }
}
