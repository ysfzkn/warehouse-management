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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kargo takip polling job'u.
 *
 * Her 30 dakikada bir SHIPPED durumundaki siparişlerin kargo takip durumunu
 * sağlayıcıdan sorgular. Teslim edilmişse order'ı DELIVERED olarak günceller.
 *
 * Kargonomi / diğer sağlayıcıların webhook desteği yoksa bu polling gereklidir.
 * Webhook desteği varsa bu job devre dışı bırakılabilir.
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
     * Her 30 dakikada bir çalışır. Uygulama başlangıcından 3 dakika sonra ilk çalışma.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    @Transactional
    public void pollShippedOrders() {
        if (!cargoApiService.isEnabled()) return;

        // SHIPPED durumundaki ve tracking numarası olan siparişleri al
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

                // Teslim edildi?
                if (status.getStatus() == CargoTrackingStatus.CargoStatus.DELIVERED) {
                    order.setStatus(OrderStatus.DELIVERED);
                    orderRepository.save(order);

                    statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                        order, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                        "system", "CARGO_TRACKING_JOB",
                        "Kargo API teslim bildirimi: " + (status.getStatusText() != null ? status.getStatusText() : "")));

                    // Müşteriye bildirim
                    try {
                        notificationDispatchService.notifyOrderStatusChange(
                                order.getCustomer(), order.getOrderNumber(),
                                "DELIVERED", order.getCargoTrackingNo(),
                                "Siparişiniz teslim edildi.");
                    } catch (Exception ignored) {}

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
