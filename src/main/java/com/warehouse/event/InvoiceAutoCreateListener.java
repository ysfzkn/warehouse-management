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
 * Sipariş ödenince fatura otomatik kesimi.
 *
 * <p><b>Mimari kararlar:</b>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — Sipariş DB'ye
 *       yazılmadan fatura kesilmez (race condition koruması).</li>
 *   <li>{@code @Async} — Logo SOAP çağrısı 2-10 saniye sürebilir; sipariş onay
 *       endpoint'i bu süreyi beklemesin. Hata olursa fatura DRAFT/ERROR
 *       statüsünde kalır, admin digest ile bildirilir.</li>
 *   <li>Feature flag: {@code invoice_auto_generate} site setting'i kapalıysa
 *       sessizce return (kullanıcı admin panelden manuel kesebilir).</li>
 *   <li>Idempotency: {@link InvoiceService#createInvoiceForOrder} mevcut faturayı
 *       kontrol ediyor (ERROR/CANCELLED dışındaysa tekrar oluşturmaz).</li>
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
            // Logo'dan gelen hataları yakalayıp event'in dağılmasını engelle —
            // fatura ERROR statüsünde kayda alındı; admin digest ile bildirilecek.
            log.error("[Invoice] Otomatik fatura kesimi başarısız: order={}, hata={}",
                    event.getOrderNumber(), e.getMessage());
        }
    }
}
