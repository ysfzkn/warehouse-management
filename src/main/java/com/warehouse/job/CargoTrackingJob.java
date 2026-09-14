package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoTrackingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cargo tracking polling job.
 *
 * <p>Every 30 minutes, asks the provider about orders that are SHIPPED and carry a tracking
 * number. Everything the answer implies — delivery, failed delivery, return — is applied by
 * {@link CargoApiService#applyTrackingUpdate}, the same entry point the Kargonomi webhook uses,
 * so both channels behave identically.
 *
 * <p>Role depends on whether webhooks are live. With webhooks configured this is a safety net:
 * it only re-checks shipments nobody has heard about for {@value #WEBHOOK_BACKSTOP_HOURS} hours,
 * which catches a webhook that was never delivered or a registration that silently lapsed.
 * Without webhooks it is the only channel, so it sweeps every shipment on each run.
 *
 * <p>Deliberately not {@code @Transactional}: the loop makes one HTTP call per order, and holding
 * a database transaction open across them was how a slow carrier turned into database pressure.
 * Each order is updated in its own transaction inside the service.
 */
@Component
public class CargoTrackingJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoTrackingJob.class);

    /** Orders examined per run — keeps a backlog from turning into a burst of API calls. */
    private static final int BATCH_SIZE = 50;

    /** With webhooks active, only shipments this stale are re-polled. */
    static final int WEBHOOK_BACKSTOP_HOURS = 24;

    private final CargoApiService cargoApiService;
    private final OrderRepository orderRepository;

    public CargoTrackingJob(CargoApiService cargoApiService,
                             OrderRepository orderRepository) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
    }

    /**
     * Runs every 30 minutes. First run is 3 minutes after application startup.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    @SchedulerLock(name = "cargoTracking", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    public void pollShippedOrders() {
        if (!cargoApiService.isEnabled()) return;

        boolean webhookActive = cargoApiService.isWebhookTrackingActive();
        LocalDateTime staleBefore = webhookActive
                ? LocalDateTime.now().minusHours(WEBHOOK_BACKSTOP_HOURS)
                : LocalDateTime.now();

        List<Order> due = orderRepository.findDueForCargoTracking(
                OrderStatus.SHIPPED, staleBefore, PageRequest.of(0, BATCH_SIZE));

        if (due.isEmpty()) return;

        int updated = 0;
        int failed = 0;

        for (Order order : due) {
            try {
                CargoTrackingStatus status = cargoApiService.fetchTrackingStatus(order);
                if (status == null) continue;
                if (cargoApiService.applyTrackingUpdate(order.getId(), status, CargoApiService.SOURCE_JOB)) {
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                logger.warn("Cargo tracking hatası (order={}): {}", order.getOrderNumber(), e.getMessage());
            }
        }

        logger.info("Cargo tracking job: {} sipariş sorgulandı, {} güncellendi, {} hata (mod: {})",
                due.size(), updated, failed, webhookActive ? "webhook yedeği" : "birincil");
    }
}
