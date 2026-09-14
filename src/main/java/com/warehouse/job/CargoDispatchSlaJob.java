package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.NotificationService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.cargo.CargoApiService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Catches parcels the carrier never actually picked up.
 *
 * <p>A shipment sits at "İşleme Hazır" until a courier collects it. If nobody comes — a missed
 * pickup, a label printed but left on the desk — nothing in the system objects: the order says
 * "Kargoda", the customer waits, and the first complaint arrives days later. This flags shipments
 * that have not moved within {@code cargo_dispatch_sla_hours}.
 */
@Component
public class CargoDispatchSlaJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoDispatchSlaJob.class);

    private static final int DEFAULT_SLA_HOURS = 24;
    private static final int BATCH_SIZE = 200;

    /** Carrier statuses that mean "created but not yet moving". */
    private static final List<String> NOT_MOVING_YET = List.of(
            "draft", "ready", "webservice_order_creating", "webservice_order_created",
            "webservice_checking_shipment");

    private final OrderRepository orderRepository;
    private final SiteSettingService settingService;
    private final NotificationService notificationService;
    private final CargoApiService cargoApiService;

    public CargoDispatchSlaJob(OrderRepository orderRepository,
                                SiteSettingService settingService,
                                NotificationService notificationService,
                                CargoApiService cargoApiService) {
        this.orderRepository = orderRepository;
        this.settingService = settingService;
        this.notificationService = notificationService;
        this.cargoApiService = cargoApiService;
    }

    /** Every weekday morning, in time to chase a pickup the same day. */
    @Scheduled(cron = "0 15 8 * * *")
    @SchedulerLock(name = "cargoDispatchSla", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void flagStuckShipments() {
        if (!cargoApiService.isEnabled()) return;

        int slaHours = slaHours();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(slaHours);

        List<Order> stuck = orderRepository
                .findDueForCargoTracking(OrderStatus.SHIPPED, LocalDateTime.now(),
                        PageRequest.of(0, BATCH_SIZE))
                .stream()
                .filter(o -> o.getUpdatedAt() != null && o.getUpdatedAt().isBefore(cutoff))
                .filter(o -> o.getCargoStatus() == null
                        || NOT_MOVING_YET.contains(o.getCargoStatus().toLowerCase()))
                .toList();

        if (stuck.isEmpty()) {
            logger.debug("Kargo SLA: bekleyen gönderi yok.");
            return;
        }

        logger.warn("Kargo SLA: {} gönderi {} saattir kargo firmasınca alınmamış görünüyor",
                stuck.size(), slaHours);

        String orders = String.join(", ", stuck.stream().limit(10).map(Order::getOrderNumber).toList());
        try {
            notificationService.create(
                    stuck.size() + " kargo " + slaHours + " saattir hareket etmedi",
                    "Bu siparişler 'Kargoda' görünüyor ama kargo firması henüz teslim almamış: "
                            + orders + (stuck.size() > 10 ? ", …" : "")
                            + ". Kargo firmasını aramak ya da paketleri tekrar teslim etmek gerekebilir.",
                    "ORDER", null);
        } catch (Exception e) {
            logger.warn("Kargo SLA bildirimi oluşturulamadı: {}", e.toString());
        }
    }

    private int slaHours() {
        String raw = settingService.getSetting("cargo_dispatch_sla_hours");
        if (raw != null && !raw.isBlank()) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_SLA_HOURS;
    }
}
