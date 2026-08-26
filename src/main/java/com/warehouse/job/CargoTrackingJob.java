package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoTrackingStatus;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.service.notification.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cargo tracking polling job.
 *
 * Every 30 minutes, queries the provider for the cargo tracking status of orders
 * in SHIPPED status. If delivered, updates the order to DELIVERED.
 *
 * This polling is required when Kargonomi / other providers do not support
 * webhooks. If webhook support is available, this job can be disabled.
 */
@Component
public class CargoTrackingJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoTrackingJob.class);
    private static final int BATCH_SIZE = 50;

    private final CargoApiService cargoApiService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final NotificationDispatchService notificationDispatchService;

    public CargoTrackingJob(CargoApiService cargoApiService,
                             OrderRepository orderRepository,
                             OrderStatusHistoryRepository statusHistoryRepository,
                             NotificationDispatchService notificationDispatchService) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.notificationDispatchService = notificationDispatchService;
    }

    /**
     * Runs every 30 minutes. First run is 3 minutes after application startup.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    @SchedulerLock(name = "cargoTracking", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    @Transactional
    public void pollShippedOrders() {
        if (!cargoApiService.isEnabled()) return;

        // Fetch orders in SHIPPED status that have a tracking number
        List<Order> shippedOrders = orderRepository.findAll(PageRequest.of(0, BATCH_SIZE))
                .stream()
                .filter(o -> o.getStatus() == OrderStatus.SHIPPED)
                .filter(o -> o.getCargoTrackingNo() != null && !o.getCargoTrackingNo().isBlank())
                .toList();

        if (shippedOrders.isEmpty()) return;

        int deliveredCount = 0;
        int failedCount = 0;

        for (Order order : shippedOrders) {
            try {
                CargoTrackingStatus status = cargoApiService.trackOrder(order);
                if (status == null) continue;

                // Delivered?
                if (status.getStatus() == CargoTrackingStatus.CargoStatus.DELIVERED) {
                    order.setStatus(OrderStatus.DELIVERED);
                    orderRepository.save(order);

                    statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                        order, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                        "system", "CARGO_TRACKING_JOB",
                        "Kargo API teslim bildirimi: " + (status.getStatusText() != null ? status.getStatusText() : "")));

                    // Notify the customer
                    try {
                        notificationDispatchService.notifyOrderStatusChange(
                                order.getCustomer(), order.getOrderNumber(),
                                "DELIVERED", order.getCargoTrackingNo(),
                                "Siparişiniz teslim edildi.");
                    } catch (Exception e) {
                        logger.warn("Teslimat bildirimi gönderilemedi (sipariş {}): {}",
                                order.getOrderNumber(), e.toString());
                    }

                    deliveredCount++;
                    logger.info("Order auto-delivered: {} (tracking: {})",
                            order.getOrderNumber(), order.getCargoTrackingNo());
                }
            } catch (Exception e) {
                failedCount++;
                logger.warn("Cargo tracking hatası (order={}): {}", order.getOrderNumber(), e.getMessage());
            }
        }

        if (deliveredCount > 0 || failedCount > 0) {
            logger.info("Cargo tracking job: {} teslim edildi, {} hatali ({} siparis tarandi)",
                    deliveredCount, failedCount, shippedOrders.size());
        }
    }
}
