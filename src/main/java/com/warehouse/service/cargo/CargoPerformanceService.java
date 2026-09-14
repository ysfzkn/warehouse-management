package com.warehouse.service.cargo;

import com.warehouse.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How each carrier is actually performing.
 *
 * <p>The carrier chosen at checkout has always been a matter of habit or price, with no way to
 * see which one loses parcels or takes five days to a district the others reach in two. This
 * turns the shipment history into that comparison: volume, delivery rate, problem rate and
 * average days in transit, per carrier.
 */
@Service
public class CargoPerformanceService {

    /** Carrier statuses that mean the delivery went wrong. */
    static final List<String> PROBLEM_STATUSES = List.of(
            "webservice_shipment_not_delivered",
            "webservice_shipment_missing",
            "webservice_shipment_returning",
            "webservice_order_failed");

    private final OrderRepository orderRepository;

    public CargoPerformanceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * One carrier's record over the window.
     *
     * @param shipped      shipments handed over
     * @param delivered    shipments that reached the customer
     * @param problems     shipments currently in a failed, lost or returning state
     * @param deliveryRate delivered / shipped, as a percentage
     * @param problemRate  problems / shipped, as a percentage
     * @param avgDays      average days from order to delivery; null when nothing was delivered yet
     */
    public record CarrierStats(String carrier, long shipped, long delivered, long problems,
                                BigDecimal deliveryRate, BigDecimal problemRate, BigDecimal avgDays) {}

    /**
     * @param days how far back to look
     * @return one row per carrier, busiest first
     */
    public List<CarrierStats> report(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(1, days));

        Map<String, BigDecimal> avgDaysByCarrier = averageDeliveryDays(from);
        List<CarrierStats> rows = new ArrayList<>();

        for (Object[] row : orderRepository.carrierPerformance(from, PROBLEM_STATUSES)) {
            String carrier = (String) row[0];
            long shipped = toLong(row[1]);
            long delivered = toLong(row[2]);
            long problems = toLong(row[3]);

            rows.add(new CarrierStats(
                    carrier, shipped, delivered, problems,
                    percentage(delivered, shipped),
                    percentage(problems, shipped),
                    avgDaysByCarrier.get(carrier)));
        }

        rows.sort((a, b) -> Long.compare(b.shipped(), a.shipped()));
        return rows;
    }

    /**
     * Average days from order to delivery, per carrier.
     *
     * <p>Computed here rather than in SQL because date subtraction is spelled differently on
     * every database; the window is a report's worth of delivered orders, not the whole table.
     */
    private Map<String, BigDecimal> averageDeliveryDays(LocalDateTime from) {
        Map<String, long[]> totals = new HashMap<>();   // carrier → [dayTotal, count]

        for (Object[] row : orderRepository.carrierDeliveryDurations(from)) {
            String carrier = (String) row[0];
            LocalDateTime createdAt = (LocalDateTime) row[1];
            LocalDate deliveredOn = (LocalDate) row[2];
            if (createdAt == null || deliveredOn == null) continue;

            long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), deliveredOn);
            if (days < 0) continue;   // clock skew or a back-dated delivery; not a real duration

            long[] acc = totals.computeIfAbsent(carrier, k -> new long[2]);
            acc[0] += days;
            acc[1]++;
        }

        Map<String, BigDecimal> out = new HashMap<>();
        totals.forEach((carrier, acc) -> {
            if (acc[1] == 0) return;
            out.put(carrier, BigDecimal.valueOf(acc[0])
                    .divide(BigDecimal.valueOf(acc[1]), 1, RoundingMode.HALF_UP));
        });
        return out;
    }

    private static BigDecimal percentage(long part, long whole) {
        if (whole <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(part * 100.0)
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
