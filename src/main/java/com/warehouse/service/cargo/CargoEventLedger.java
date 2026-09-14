package com.warehouse.service.cargo;

import com.warehouse.entity.CargoShipmentEvent;
import com.warehouse.entity.Order;
import com.warehouse.repository.CargoShipmentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes an order's cargo history down.
 *
 * <p>The carrier's movement list used to be parsed and thrown away, so nobody could answer
 * "where did this parcel get stuck" after the fact — the only surviving trace was the current
 * status, which the carrier overwrites. Every status change and every movement now lands here.
 *
 * <p>Runs in its own transaction ({@code REQUIRES_NEW}) and swallows its own failures: the
 * ledger is an observer of the cargo flow, never a reason for it to fail.
 */
@Service
public class CargoEventLedger {

    private static final Logger logger = LoggerFactory.getLogger(CargoEventLedger.class);

    private final CargoShipmentEventRepository eventRepository;

    public CargoEventLedger(CargoShipmentEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * @param statusChanged whether the carrier's own status differs from what we had stored;
     *                      a status row is only written when it actually moved
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Order order, CargoTrackingStatus status, String source, boolean statusChanged) {
        if (order == null || status == null) return;
        try {
            if (statusChanged) recordStatusChange(order, status, source);
            recordMovements(order, status, source);
        } catch (Exception e) {
            logger.warn("Kargo olay defteri yazılamadı (sipariş {}): {}",
                    order.getOrderNumber(), e.toString());
        }
    }

    private void recordStatusChange(Order order, CargoTrackingStatus status, String source) {
        String code = status.getStatusText() != null && !status.getStatusText().isBlank()
                ? status.getStatusText()
                : String.valueOf(status.getStatus());

        if (eventRepository.existsByOrderIdAndStatusCodeAndOccurredAtIsNull(order.getId(), code)) return;

        CargoShipmentEvent event = baseEvent(order, source);
        event.setStatusCode(code);
        event.setStatusLabel(KargonomiCargoProvider.statusLabel(code));
        event.setMappedStatus(status.getStatus() != null ? status.getStatus().name() : null);
        event.setDescription(event.getStatusLabel());
        eventRepository.save(event);
    }

    private void recordMovements(Order order, CargoTrackingStatus status, String source) {
        if (status.getEvents() == null || status.getEvents().isEmpty()) return;

        for (CargoTrackingStatus.TrackingEvent movement : status.getEvents()) {
            LocalDateTime occurredAt = movement.getTimestamp();
            // Without a timestamp there is nothing to deduplicate on, and the carrier resends the
            // whole list on every query — so an undated movement would pile up on each poll.
            if (occurredAt == null) continue;

            String code = movement.getStatus() != null ? movement.getStatus().name() : "MOVEMENT";
            if (eventRepository.existsByOrderIdAndStatusCodeAndOccurredAt(order.getId(), code, occurredAt)) {
                continue;
            }

            CargoShipmentEvent event = baseEvent(order, source);
            event.setStatusCode(code);
            event.setMappedStatus(code);
            event.setDescription(truncate(movement.getDescription(), 500));
            event.setLocation(truncate(movement.getLocation(), 200));
            event.setOccurredAt(occurredAt);
            eventRepository.save(event);
        }
    }

    private CargoShipmentEvent baseEvent(Order order, String source) {
        CargoShipmentEvent event = new CargoShipmentEvent();
        event.setOrderId(order.getId());
        event.setOrderNumber(order.getOrderNumber());
        event.setTrackingNo(order.getCargoTrackingNo());
        event.setSource(source);
        return event;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
