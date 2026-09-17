package com.warehouse.job;

import com.warehouse.service.ScheduledDeliveryReminderService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planlı teslimatların günlük taraması.
 *
 * <p>Sabah 08:00: depo günü buradan planlıyor. Bir gün öncesinin hatırlatması akşamdan
 * değil sabahtan gidiyor çünkü hazırlık mesai içinde yapılıyor; 17:00'de düşen "yarın
 * teslim" maili ertesi sabaha kadar okunmuyor ve hazırlık için bir gün değil, birkaç saat
 * bırakıyor.</p>
 *
 * <p>Günde bir kez yeterli, çünkü aşamalar gün bazında hesaplanıyor. Gün içinde kurulan
 * planların kaçırılmaması job'ın sıklığıyla değil, plan kaydedilir kaydedilmez aynı
 * taramanın o sevkiyat için çalıştırılmasıyla çözülüyor
 * ({@link com.warehouse.event.ScheduledDeliveryPlannedEvent}).</p>
 *
 * <p>İnce iş {@link ScheduledDeliveryReminderService} içinde: job yalnızca zamanlayıcı ve
 * kilit. Aynı mantığı olay dinleyicisi de çağırıyor, ve tek kopya olması iki yolun
 * ayrışmamasının tek garantisi.</p>
 */
@Component
@Profile("!test")
public class ScheduledDeliveryReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDeliveryReminderJob.class);

    private final ScheduledDeliveryReminderService reminderService;

    public ScheduledDeliveryReminderJob(ScheduledDeliveryReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Istanbul")
    @SchedulerLock(name = "scheduledDeliveryReminder", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void sendReminders() {
        try {
            reminderService.sweepAll();
        } catch (Exception e) {
            // Job'ın kendisi ölmemeli: bir sonraki sabah yeniden koşuyor ve gönderilmemiş
            // aşamalar damgasız durduğu için kaldığı yerden devam ediyor.
            log.error("[TeslimatHatirlatma] Günlük tarama hata verdi: {}", e.getMessage(), e);
        }
    }
}
