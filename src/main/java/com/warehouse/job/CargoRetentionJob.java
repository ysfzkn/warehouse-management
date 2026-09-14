package com.warehouse.job;

import com.warehouse.repository.CargoWebhookDeliveryRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Keeps the raw webhook log from growing without end.
 *
 * <p>Stored payloads exist to answer "what did the carrier actually send us" while a problem is
 * still fresh. After {@value #RETENTION_DAYS} days they are noise: the shipment history that
 * matters lives in {@code cargo_shipment_events}, which is never purged.
 */
@Component
public class CargoRetentionJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoRetentionJob.class);

    static final int RETENTION_DAYS = 30;

    private final CargoWebhookDeliveryRepository deliveryRepository;

    public CargoRetentionJob(CargoWebhookDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    /** Nightly, well outside business hours. */
    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "cargoWebhookRetention", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeOldWebhookDeliveries() {
        int deleted = deliveryRepository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            logger.info("Kargo webhook günlüğü temizlendi: {} kayıt silindi ({} günden eski)",
                    deleted, RETENTION_DAYS);
        }
    }
}
