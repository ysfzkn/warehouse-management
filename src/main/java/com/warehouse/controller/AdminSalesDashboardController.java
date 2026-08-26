package com.warehouse.controller;

import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.PaymentTransactionRepository;
import com.warehouse.repository.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/admin/sales-dashboard")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminSalesDashboardController {

    private final OrderRepository orderRepo;
    private final PaymentTransactionRepository paymentRepo;
    private final CustomerRepository customerRepo;

    public AdminSalesDashboardController(OrderRepository orderRepo,
                                          PaymentTransactionRepository paymentRepo,
                                          CustomerRepository customerRepo) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.customerRepo = customerRepo;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) String period) {

        LocalDateTime startDate = getStartDate(period);
        Map<String, Object> summary = new LinkedHashMap<>();

        // Order counts by status
        long totalOrders = orderRepo.count();
        long pendingPayment = orderRepo.countByStatus(OrderStatus.PENDING_PAYMENT);
        long paid = orderRepo.countByStatus(OrderStatus.PAID);
        long preparing = orderRepo.countByStatus(OrderStatus.PREPARING);
        long shipped = orderRepo.countByStatus(OrderStatus.SHIPPED);
        long delivered = orderRepo.countByStatus(OrderStatus.DELIVERED);
        long cancelled = orderRepo.countByStatus(OrderStatus.CANCELLED);
        long returnRequested = orderRepo.countByStatus(OrderStatus.RETURN_REQUESTED);

        summary.put("totalOrders", totalOrders);
        summary.put("pendingPayment", pendingPayment);
        summary.put("paid", paid);
        summary.put("preparing", preparing);
        summary.put("shipped", shipped);
        summary.put("delivered", delivered);
        summary.put("cancelled", cancelled);
        summary.put("returnRequested", returnRequested);
        summary.put("activeOrders", paid + preparing + shipped);

        // Revenue for the period — grouped in SQL, one row per status.
        Set<OrderStatus> revenueStatuses = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal deliveredRevenue = BigDecimal.ZERO;
        long periodOrders = 0;
        for (Object[] row : orderRepo.aggregateByStatusSince(startDate == null ? EPOCH : startDate)) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal revenue = toAmount(row[2]);
            periodOrders += count;
            if (revenueStatuses.contains(status)) totalRevenue = totalRevenue.add(revenue);
            if (status == OrderStatus.DELIVERED) deliveredRevenue = deliveredRevenue.add(revenue);
        }
        BigDecimal avgOrderValue = periodOrders > 0
            ? totalRevenue.divide(BigDecimal.valueOf(periodOrders), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        summary.put("totalRevenue", totalRevenue);
        summary.put("deliveredRevenue", deliveredRevenue);
        summary.put("avgOrderValue", avgOrderValue);
        summary.put("periodOrders", periodOrders);

        // Customer stats
        summary.put("totalCustomers", customerRepo.count());

        // Conversion rate (delivered / total non-cancelled)
        long nonCancelled = totalOrders - cancelled;
        double conversionRate = nonCancelled > 0 ? (double) delivered / nonCancelled * 100 : 0;
        summary.put("conversionRate", Math.round(conversionRate * 10.0) / 10.0);

        // Previous period comparison
        if (startDate != null) {
            java.time.Duration duration = java.time.Duration.between(startDate, LocalDateTime.now());
            LocalDateTime prevStart = startDate.minus(duration);
            LocalDateTime prevEnd = startDate;

            Object[] prev = orderRepo.aggregateBetween(prevStart, prevEnd);
            long prevOrders = prev == null ? 0 : ((Number) prev[0]).longValue();
            BigDecimal prevRevenue = prev == null ? BigDecimal.ZERO : toAmount(prev[1]);

            double revenueChange = prevRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalRevenue.subtract(prevRevenue).doubleValue() / prevRevenue.doubleValue() * 100 : 0;
            double ordersChange = prevOrders > 0
                ? (double)(periodOrders - prevOrders) / prevOrders * 100 : 0;

            summary.put("revenueChange", Math.round(revenueChange * 10.0) / 10.0);
            summary.put("ordersChange", Math.round(ordersChange * 10.0) / 10.0);
            summary.put("prevRevenue", prevRevenue);
            summary.put("prevOrders", prevOrders);
        }

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/hourly-heatmap")
    public ResponseEntity<List<Map<String, Object>>> getHourlyHeatmap(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime startDate = LocalDate.now().minusDays(days).atStartOfDay();

        // 7 days x 24 hours matrix
        int[][] heatmap = new int[7][24]; // [dayOfWeek][hour]

        for (Object[] row : orderRepo.findCreatedAtAndTotalSince(startDate)) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            if (createdAt == null) continue;
            int dow = createdAt.getDayOfWeek().getValue() - 1; // 0=Mon, 6=Sun
            heatmap[dow][createdAt.getHour()]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String[] dayNames = {"Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"};
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("day", d);
                cell.put("dayName", dayNames[d]);
                cell.put("hour", h);
                cell.put("count", heatmap[d][h]);
                result.add(cell);
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> live = new LinkedHashMap<>();

        // Polled every 30s by the dashboard — must stay a single aggregate query.
        Object[] today = orderRepo.aggregateBetween(todayStart, todayStart.plusDays(1));
        long todayCount = today == null ? 0 : ((Number) today[0]).longValue();
        BigDecimal todayRevenue = today == null ? BigDecimal.ZERO : toAmount(today[1]);

        live.put("todayOrders", todayCount);
        live.put("todayRevenue", todayRevenue);
        live.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(live);
    }

    @GetMapping("/daily-revenue")
    public ResponseEntity<List<Map<String, Object>>> getDailyRevenue(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime startDate = LocalDate.now().minusDays(days).atStartOfDay();
        List<Map<String, Object>> daily = new ArrayList<>();

        // Group orders by day
        Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> ordersByDay = new LinkedHashMap<>();

        // Initialize all days
        for (int i = days; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            revenueByDay.put(date, BigDecimal.ZERO);
            ordersByDay.put(date, 0L);
        }

        for (Object[] row : orderRepo.findCreatedAtAndTotalSince(startDate)) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            if (createdAt == null) continue;
            LocalDate day = createdAt.toLocalDate();
            // merge() only counts days seeded above; older rows would create stray buckets.
            if (!revenueByDay.containsKey(day)) continue;
            revenueByDay.merge(day, toAmount(row[1]), BigDecimal::add);
            ordersByDay.merge(day, 1L, Long::sum);
        }

        for (Map.Entry<LocalDate, BigDecimal> entry : revenueByDay.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey().toString());
            point.put("revenue", entry.getValue());
            point.put("orders", ordersByDay.getOrDefault(entry.getKey(), 0L));
            daily.add(point);
        }

        return ResponseEntity.ok(daily);
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {

        // Grouped and ordered in SQL, limited to the requested rows — the previous version
        // walked every order's item collection (a full scan plus one query per order).
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> topProducts = orderRepo.aggregateTopProducts(PageRequest.of(0, safeLimit))
            .stream()
            .map(row -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", row[0] == null ? "Ürün" : String.valueOf(row[0]));
                p.put("quantity", ((Number) row[1]).longValue());
                p.put("revenue", toAmount(row[2]));
                return p;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(topProducts);
    }

    @GetMapping("/payment-breakdown")
    public ResponseEntity<List<Map<String, Object>>> getPaymentBreakdown() {
        // [method -> {count, revenue}] straight from SQL; revenue stays BigDecimal so the
        // kuruş are not truncated the way the old longValue() conversion did.
        Map<String, Object[]> breakdown = new LinkedHashMap<>();
        for (Object[] row : orderRepo.aggregateByPaymentMethod()) {
            breakdown.put(String.valueOf(row[0]),
                    new Object[]{((Number) row[1]).longValue(), toAmount(row[2])});
        }

        Map<String, String> labels = Map.of(
            "CREDIT_CARD", "Kredi Kartı", "BANK_TRANSFER", "Havale/EFT",
            "DOOR_CASH", "Kapıda Nakit", "DOOR_CARD", "Kapıda Kart",
            "VIRTUAL_POS", "Sanal POS"
        );

        return ResponseEntity.ok(breakdown.entrySet().stream()
            .map(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("method", e.getKey());
                item.put("label", labels.getOrDefault(e.getKey(), e.getKey()));
                item.put("count", e.getValue()[0]);
                item.put("revenue", e.getValue()[1]);
                return item;
            }).collect(Collectors.toList()));
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<List<Map<String, Object>>> getRecentOrders(
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(orderRepo.findAll(
            org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 100)),
                org.springframework.data.domain.Sort.by("createdAt").descending())
        ).getContent().stream().map(o -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", o.getId());
            dto.put("orderNumber", o.getOrderNumber());
            dto.put("status", o.getStatus() != null ? o.getStatus().name() : null);
            dto.put("grandTotal", o.getGrandTotal());
            dto.put("paymentMethod", o.getPaymentMethod());
            dto.put("createdAt", o.getCreatedAt());
            try {
                dto.put("customerName", o.getCustomer() != null
                    ? o.getCustomer().getFirstName() + " " + o.getCustomer().getLastName() : "");
            } catch (Exception e) { dto.put("customerName", ""); }
            return dto;
        }).collect(Collectors.toList()));
    }

    /** Floor used when no period is selected — "since forever" without a null-guard in JPQL. */
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    /** SUM() comes back as BigDecimal on Postgres and as a plain Number elsewhere. */
    private static BigDecimal toAmount(Object raw) {
        if (raw == null) return BigDecimal.ZERO;
        if (raw instanceof BigDecimal bd) return bd;
        return BigDecimal.valueOf(((Number) raw).doubleValue());
    }

    private LocalDateTime getStartDate(String period) {
        if (period == null) return null;
        return switch (period) {
            case "today" -> LocalDate.now().atStartOfDay();
            case "week" -> LocalDate.now().minusWeeks(1).atStartOfDay();
            case "month" -> LocalDate.now().minusMonths(1).atStartOfDay();
            case "quarter" -> LocalDate.now().minusMonths(3).atStartOfDay();
            case "year" -> LocalDate.now().minusYears(1).atStartOfDay();
            default -> null;
        };
    }
}
