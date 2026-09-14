package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.NotificationService;
import com.warehouse.service.cargo.CargoApiService;
import com.warehouse.service.cargo.CargoTrackingStatus;
import com.warehouse.service.cargo.KargonomiCargoProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares what the carrier thinks with what we think, once a day.
 *
 * <p>Webhooks and the polling job both assume they will eventually hear about every shipment.
 * Neither notices a webhook that was never delivered while its registration had lapsed, or a
 * shipment created in the Kargonomi panel by hand that our database has never seen. This walks
 * the carrier's own shipment list and reports the differences.
 *
 * <p>Reports, does not repair. A mismatch is a symptom — closing it automatically would hide the
 * cause, and the cases differ too much to guess at. The exception is a status we simply missed:
 * that is applied through the normal path, because it is not ambiguous.
 */
@Component
public class CargoReconciliationJob {

    private static final Logger logger = LoggerFactory.getLogger(CargoReconciliationJob.class);

    /** Kargonomi paginates at 50; a few pages cover a normal day comfortably. */
    private static final int MAX_PAGES = 5;

    private final CargoApiService cargoApiService;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    public CargoReconciliationJob(CargoApiService cargoApiService,
                                   OrderRepository orderRepository,
                                   NotificationService notificationService) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
    }

    /** 04:00, after the night's retention pass and before anyone starts dispatching. */
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "cargoReconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    public void reconcile() {
        if (!cargoApiService.isEnabled()) return;
        if (!(cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider provider)) return;

        List<String> unknownShipments = new ArrayList<>();
        int checked = 0;
        int statusesCaughtUp = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            List<Map<String, Object>> shipments = readPage(provider, page);
            if (shipments.isEmpty()) break;

            for (Map<String, Object> shipment : shipments) {
                checked++;
                Order order = findOrder(shipment);
                if (order == null) {
                    String id = String.valueOf(shipment.getOrDefault("id", "?"));
                    unknownShipments.add(id);
                    continue;
                }
                // A status we never heard about — apply it through the usual path, which knows
                // what each status is allowed to do to an order.
                CargoTrackingStatus status = provider.parseTrackingResponse(shipment);
                if (status.getStatusText() != null
                        && !status.getStatusText().equalsIgnoreCase(order.getCargoStatus())) {
                    cargoApiService.applyTrackingUpdate(order.getId(), status, CargoApiService.SOURCE_JOB);
                    statusesCaughtUp++;
                }
            }
        }

        List<Order> shippedWithoutShipment = ordersMissingAShipment();

        logger.info("Kargo mutabakatı: {} gönderi tarandı, {} durum yakalandı, {} eşleşmeyen gönderi, "
                + "{} kargosuz SHIPPED sipariş", checked, statusesCaughtUp,
                unknownShipments.size(), shippedWithoutShipment.size());

        if (statusesCaughtUp > 0) {
            logger.warn("Kargo mutabakatı {} kaçmış durum güncellemesi yakaladı — webhook kaydı "
                    + "çalışmıyor olabilir, Kargonomi panelinden kontrol edin.", statusesCaughtUp);
        }
        reportDifferences(unknownShipments, shippedWithoutShipment, statusesCaughtUp);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPage(KargonomiCargoProvider provider, int page) {
        try {
            Map<String, Object> body = provider.listShipments(page);
            if (body == null) return List.of();
            Object data = body.getOrDefault("data", body.get("shipments"));
            if (data instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
                }
                return out;
            }
            return List.of();
        } catch (Exception e) {
            logger.warn("Kargo mutabakatı — sayfa {} okunamadı: {}", page, e.toString());
            return List.of();
        }
    }

    private Order findOrder(Map<String, Object> shipment) {
        String shipmentId = String.valueOf(shipment.getOrDefault("id", ""));
        if (!shipmentId.isBlank()) {
            Optional<Order> byProvider = orderRepository.findByCargoProviderShipmentId(shipmentId);
            if (byProvider.isPresent()) return byProvider.get();
        }
        String trackingCode = String.valueOf(
                shipment.getOrDefault("shipping_webservice_tracking_code", ""));
        if (trackingCode.isBlank()) trackingCode = String.valueOf(shipment.getOrDefault("tracking_code", ""));
        if (!trackingCode.isBlank() && !"null".equals(trackingCode)) {
            return orderRepository.findByCargoTrackingNo(trackingCode).orElse(null);
        }
        return null;
    }

    /** Orders we believe are in transit but for which no shipment was ever created. */
    private List<Order> ordersMissingAShipment() {
        return orderRepository.findDueForCargoTracking(
                        OrderStatus.SHIPPED, LocalDateTime.now(),
                        org.springframework.data.domain.PageRequest.of(0, 200))
                .stream()
                .filter(o -> o.getCargoProviderShipmentId() == null
                        || o.getCargoProviderShipmentId().isBlank())
                .toList();
    }

    private void reportDifferences(List<String> unknownShipments, List<Order> shippedWithoutShipment,
                                    int statusesCaughtUp) {
        if (unknownShipments.isEmpty() && shippedWithoutShipment.isEmpty() && statusesCaughtUp == 0) {
            return;
        }
        StringBuilder message = new StringBuilder();
        if (statusesCaughtUp > 0) {
            message.append(statusesCaughtUp)
                   .append(" gönderinin durumu bize ulaşmamıştı, mutabakatta yakalandı. ");
        }
        if (!unknownShipments.isEmpty()) {
            message.append(unknownShipments.size())
                   .append(" Kargonomi gönderisi hiçbir siparişle eşleşmedi (")
                   .append(String.join(", ", unknownShipments.stream().limit(5).toList()))
                   .append(unknownShipments.size() > 5 ? ", …" : "")
                   .append("). ");
        }
        if (!shippedWithoutShipment.isEmpty()) {
            message.append(shippedWithoutShipment.size())
                   .append(" sipariş 'Kargoda' görünüyor ama kargo kaydı yok (")
                   .append(String.join(", ", shippedWithoutShipment.stream()
                           .limit(5).map(Order::getOrderNumber).toList()))
                   .append(shippedWithoutShipment.size() > 5 ? ", …" : "")
                   .append(").");
        }

        try {
            notificationService.create("Kargo mutabakat farkı", message.toString().trim(), "ORDER", null);
        } catch (Exception e) {
            logger.warn("Kargo mutabakat bildirimi oluşturulamadı: {}", e.toString());
        }
    }
}
