package com.warehouse.job;

import com.warehouse.service.StockNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Back-in-stock notification scheduled job.
 *
 * Runs every 15 minutes and checks pending subscriptions.
 * If a product's stock has increased, sends an email to all pending subscribers.
 */
@Component
public class StockNotificationJob {

    private static final Logger logger = LoggerFactory.getLogger(StockNotificationJob.class);

    private final StockNotificationService stockNotificationService;

    public StockNotificationJob(StockNotificationService stockNotificationService) {
        this.stockNotificationService = stockNotificationService;
    }

    /**
     * Runs every 15 minutes, with the first run 2 minutes after application startup.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    @SchedulerLock(name = "stockNotification", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    public void checkBackInStock() {
        try {
            int count = stockNotificationService.processPendingSubscriptions();
            if (count > 0) {
                logger.info("Stokta yoksa bildir: {} bildirim gönderildi", count);
            }
        } catch (Exception e) {
            logger.error("Stokta yoksa bildir job hatası: {}", e.getMessage(), e);
        }
    }
}
