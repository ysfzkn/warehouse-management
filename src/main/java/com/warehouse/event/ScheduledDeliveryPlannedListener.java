package com.warehouse.event;

import com.warehouse.service.ScheduledDeliveryReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Yeni kurulan ya da ertelenen bir planın hatırlatmasını hemen değerlendirir.
 *
 * <p>Günlük job sabah koşuyor. Öğleden sonra bugüne ya da yarına kurulan bir teslimat, o
 * günün taraması geçtiği için hem "bugün teslim" hem "yarın teslim" penceresini kaçırır ve
 * ancak günler sonra gecikme uyarısı olarak görünürdü — tam da hatırlatmanın işe yarayacağı
 * an sessiz kalarak.</p>
 *
 * <p>Aşamayı burada hesaplamıyor, servisin aynı taramasını tek sevkiyat için çağırıyor:
 * "vakti gelmiş aşama gönderilir" kuralının tek bir kopyası var, ve damgalar sayesinde
 * ertesi sabah job aynı hatırlatmayı tekrarlamıyor.</p>
 *
 * <p>{@code AFTER_COMMIT}: hatırlatma sevkiyatı veritabanından geri okuyor. Commit
 * edilmemiş bir kayıt için mail atmak, geç kalmış bir mailden çok daha kötü.</p>
 */
@Component
public class ScheduledDeliveryPlannedListener {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDeliveryPlannedListener.class);

    private final ScheduledDeliveryReminderService reminderService;

    public ScheduledDeliveryPlannedListener(ScheduledDeliveryReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryPlanned(ScheduledDeliveryPlannedEvent event) {
        try {
            reminderService.sweepOne(event.getTransferId());
        } catch (Exception e) {
            // Hatırlatma kaçsa bile depo çıkışının kendisi kaydedilmiş durumda; hata
            // buradan dışarı taşarsa asenkron yürütücünün log'unda kaybolur.
            log.error("[TeslimatHatirlatma] Plan kurulurken tarama hata verdi. transferId={}, hata={}",
                    event.getTransferId(), e.getMessage(), e);
        }
    }
}
