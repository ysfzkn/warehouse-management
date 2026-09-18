package com.warehouse.job;

import com.warehouse.constants.SettingKeys;
import com.warehouse.service.NotificationService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoBalance;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Warns before the cargo account runs dry.
 *
 * <p>Kargonomi is prepaid: at zero balance every shipment creation simply fails, and without this
 * the first person to notice is a customer whose order never shipped. The balance endpoint was
 * already wired but only ever called when an admin happened to open the cargo screen.
 *
 * <p>Runs twice a day rather than hourly — the balance falls at the pace of orders, and an alert
 * repeated every hour is an alert nobody reads.
 */
@Component
public class CargoBalanceAlertJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoBalanceAlertJob.class);

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("250");

    private final CargoApiService cargoApiService;
    private final SiteSettingService settingService;
    private final NotificationService notificationService;

    public CargoBalanceAlertJob(CargoApiService cargoApiService,
                                 SiteSettingService settingService,
                                 NotificationService notificationService) {
        this.cargoApiService = cargoApiService;
        this.settingService = settingService;
        this.notificationService = notificationService;
    }

    /** 09:00 and 17:00 — start of the day, and before the evening dispatch run. */
    @Scheduled(cron = "0 0 9,17 * * *")
    @SchedulerLock(name = "cargoBalanceAlert", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void checkBalance() {
        if (!cargoApiService.isEnabled()) return;

        CargoBalance balance = cargoApiService.getProviderBalance();

        switch (balance.state()) {
            case UNSUPPORTED -> {
                logger.debug("Kargo bakiyesi sorgulanmadı — entegrasyon kapalı ya da desteklenmiyor.");
                return;
            }
            case UNREACHABLE -> {
                // Not an alert on its own: a carrier that is down comes back, and the outbox
                // already covers the shipments that failed meanwhile.
                logger.warn("Kargo bakiyesi sorgulanamadı — kargo firmasına ulaşılamıyor.");
                return;
            }
            case NOT_REPORTED -> {
                // The carrier answered and reported no credit. On a prepaid account that means
                // no shipment can be created at all, which used to pass silently as "unknown".
                alert("Kargo hesabında bakiye görünmüyor", balance.describe());
                return;
            }
            case OK -> { /* fall through to the threshold check */ }
        }

        BigDecimal amount = balance.amount();
        BigDecimal threshold = threshold();
        if (amount.compareTo(threshold) >= 0) {
            logger.debug("Kargo bakiyesi yeterli: {} TL", amount);
            return;
        }

        logger.warn("Kargo bakiyesi düşük: {} TL (eşik {} TL)", amount, threshold);
        alert("Kargo bakiyesi düşük: " + amount + " TL",
                "Kargo hesabındaki bakiye " + threshold + " TL eşiğinin altına indi. "
                        + "Bakiye bitince yeni kargo gönderileri oluşturulamaz ve siparişler "
                        + "kuyruğa alınır.");
    }

    private void alert(String title, String message) {
        try {
            notificationService.create(title, message, "ORDER", null);
        } catch (Exception e) {
            logger.warn("Kargo bakiye bildirimi oluşturulamadı: {}", e.toString());
        }
    }

    private BigDecimal threshold() {
        String raw = settingService.getSetting(SettingKeys.CARGO_BALANCE_ALERT_THRESHOLD);
        if (raw != null && !raw.isBlank()) {
            try {
                BigDecimal value = new BigDecimal(raw.trim());
                if (value.signum() > 0) return value;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_THRESHOLD;
    }
}
