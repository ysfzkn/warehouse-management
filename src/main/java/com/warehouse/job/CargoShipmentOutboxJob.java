package com.warehouse.job;

import com.warehouse.entity.CargoShipmentOutbox;
import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.CargoShipmentOutboxRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoShipmentOutboxService;
import com.warehouse.service.cargo.CargoShipmentResult;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Retries shipments the carrier refused or never answered.
 *
 * <p>Runs every five minutes and picks up outbox entries whose backoff has elapsed. Each attempt
 * goes through {@link CargoApiService#attemptShipment} rather than {@code createShipmentForOrder},
 * so a retry records its own outcome instead of queueing itself again.
 *
 * <p>Entries are dropped without an attempt when the order no longer needs one — cancelled, or
 * already carrying a tracking number because someone created the shipment by hand.
 */
@Component
public class CargoShipmentOutboxJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoShipmentOutboxJob.class);

    private static final int BATCH_SIZE = 25;

    private final CargoApiService cargoApiService;
    private final CargoShipmentOutboxService outboxService;
    private final CargoShipmentOutboxRepository outboxRepository;
    private final OrderRepository orderRepository;

    public CargoShipmentOutboxJob(CargoApiService cargoApiService,
                                   CargoShipmentOutboxService outboxService,
                                   CargoShipmentOutboxRepository outboxRepository,
                                   OrderRepository orderRepository) {
        this.cargoApiService = cargoApiService;
        this.outboxService = outboxService;
        this.outboxRepository = outboxRepository;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 4 * 60 * 1000)
    @SchedulerLock(name = "cargoShipmentOutbox", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void retryDueShipments() {
        if (!cargoApiService.isEnabled()) return;

        List<CargoShipmentOutbox> due = outboxRepository.findDue(
                CargoShipmentOutbox.STATUS_PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) return;

        int created = 0;
        int abandoned = 0;
        int skipped = 0;

        for (CargoShipmentOutbox entry : due) {
            try {
                Order order = orderRepository.findById(entry.getOrderId()).orElse(null);

                if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
                    outboxService.markSucceeded(entry.getOrderId());  // nothing left to ship
                    skipped++;
                    continue;
                }
                if (order.getCargoTrackingNo() != null && !order.getCargoTrackingNo().isBlank()) {
                    outboxService.markSucceeded(entry.getOrderId());  // created by hand in the meantime
                    skipped++;
                    continue;
                }

                CargoShipmentResult result = cargoApiService.attemptShipment(order);
                if (result != null && result.isSuccess()) {
                    outboxService.markSucceeded(entry.getOrderId());
                    created++;
                    logger.info("Kargo outbox yeniden denemesi başarılı: order={}, takip={}",
                            entry.getOrderNumber(), result.getTrackingNumber());
                } else {
                    String code = result != null ? result.getErrorCode() : "NO_PROVIDER";
                    String message = result != null ? result.getErrorMessage()
                            : "Aktif kargo sağlayıcı yok ya da entegrasyon kapalı.";
                    if (outboxService.recordRetryFailure(entry, code, message)) abandoned++;
                }
            } catch (Exception e) {
                logger.warn("Kargo outbox denemesi hata verdi (order={}): {}",
                        entry.getOrderNumber(), e.toString());
                if (outboxService.recordRetryFailure(entry, "EXCEPTION", e.getMessage())) abandoned++;
            }
        }

        logger.info("Kargo outbox: {} kayıt denendi, {} oluşturuldu, {} düştü, {} gereksizdi",
                due.size(), created, abandoned, skipped);
    }
}
