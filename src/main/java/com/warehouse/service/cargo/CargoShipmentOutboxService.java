package com.warehouse.service.cargo;

import com.warehouse.entity.CargoShipmentOutbox;
import com.warehouse.entity.Order;
import com.warehouse.repository.CargoShipmentOutboxRepository;
import com.warehouse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Bookkeeping for shipments the carrier would not accept.
 *
 * <p>When the API is down, the customer is told the order is queued for a later attempt. It now
 * genuinely is: a failed creation lands in {@code cargo_shipment_outbox} and
 * {@link com.warehouse.job.CargoShipmentOutboxJob} retries it on a widening backoff. After
 * {@link CargoShipmentOutbox#MAX_ATTEMPTS} the entry is abandoned and an admin is told — an
 * order that cannot ship is a person's problem, not a machine's.
 *
 * <p>Deliberately holds no reference to {@link CargoApiService}: this class records outcomes,
 * the job decides when to try again. That keeps the dependency one-way.
 */
@Service
public class CargoShipmentOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(CargoShipmentOutboxService.class);

    private final CargoShipmentOutboxRepository outboxRepository;
    private final NotificationService notificationService;

    public CargoShipmentOutboxService(CargoShipmentOutboxRepository outboxRepository,
                                       NotificationService notificationService) {
        this.outboxRepository = outboxRepository;
        this.notificationService = notificationService;
    }

    /**
     * Records that an order still owes a shipment. Idempotent: a second failure for the same
     * order updates the existing entry instead of queueing it twice.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(Order order, String errorCode, String errorMessage) {
        try {
            CargoShipmentOutbox entry = outboxRepository.findByOrderId(order.getId())
                    .orElseGet(() -> {
                        CargoShipmentOutbox fresh = new CargoShipmentOutbox();
                        fresh.setOrderId(order.getId());
                        fresh.setOrderNumber(order.getOrderNumber());
                        return fresh;
                    });

            if (CargoShipmentOutbox.STATUS_SUCCEEDED.equals(entry.getStatus())) {
                // Shipped since; a stale failure must not reopen it.
                return;
            }

            entry.setStatus(CargoShipmentOutbox.STATUS_PENDING);
            entry.setLastErrorCode(truncate(errorCode, 60));
            entry.setLastError(truncate(errorMessage, 500));
            entry.setNextAttemptAt(entry.backoffFrom(LocalDateTime.now()));
            outboxRepository.save(entry);

            logger.info("Kargo gönderi kuyruğa alındı: order={}, deneme={}, hata={}",
                    order.getOrderNumber(), entry.getAttempts(), errorCode);
        } catch (Exception e) {
            logger.error("Kargo outbox kaydı yazılamadı (sipariş {}): {}",
                    order.getOrderNumber(), e.toString());
        }
    }

    /** The shipment exists now — whether it was the first try or the fifth. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long orderId) {
        outboxRepository.findByOrderId(orderId).ifPresent(entry -> {
            if (CargoShipmentOutbox.STATUS_SUCCEEDED.equals(entry.getStatus())) return;
            entry.setStatus(CargoShipmentOutbox.STATUS_SUCCEEDED);
            entry.setLastError(null);
            entry.setLastErrorCode(null);
            outboxRepository.save(entry);
            logger.info("Kargo outbox kapandı: order={} (deneme {})",
                    entry.getOrderNumber(), entry.getAttempts());
        });
    }

    /**
     * One retry has failed. Schedules the next attempt, or gives up and asks for a human.
     *
     * @return true if the entry was abandoned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRetryFailure(CargoShipmentOutbox entry, String errorCode, String errorMessage) {
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setLastErrorCode(truncate(errorCode, 60));
        entry.setLastError(truncate(errorMessage, 500));

        boolean abandoned = entry.getAttempts() >= CargoShipmentOutbox.MAX_ATTEMPTS;
        if (abandoned) {
            entry.setStatus(CargoShipmentOutbox.STATUS_ABANDONED);
            outboxRepository.save(entry);
            alertAdmin(entry, errorMessage);
        } else {
            entry.setNextAttemptAt(entry.backoffFrom(LocalDateTime.now()));
            outboxRepository.save(entry);
        }
        return abandoned;
    }

    /** Entries still waiting — surfaced on the admin cargo screen. */
    public long pendingCount() {
        return outboxRepository.countByStatus(CargoShipmentOutbox.STATUS_PENDING);
    }

    private void alertAdmin(CargoShipmentOutbox entry, String errorMessage) {
        logger.error("Kargo gönderisi {} denemede oluşturulamadı — elle oluşturulmalı: order={}, hata={}",
                entry.getAttempts(), entry.getOrderNumber(), errorMessage);
        try {
            notificationService.create(
                    "Kargo oluşturulamadı: " + entry.getOrderNumber(),
                    "Sipariş için " + entry.getAttempts() + " denemede kargo gönderisi oluşturulamadı. "
                            + "Kargonomi panelinden elle oluşturulması gerekiyor. Son hata: "
                            + (errorMessage != null ? errorMessage : "bilinmiyor"),
                    "ORDER", entry.getOrderId());
        } catch (Exception e) {
            logger.warn("Kargo outbox admin bildirimi oluşturulamadı: {}", e.toString());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
