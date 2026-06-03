package com.warehouse.assistant.store.tools;

import com.warehouse.assistant.core.security.AssistantContext;
import com.warehouse.assistant.core.security.AssistantContextHolder;
import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Customer-authenticated order lookup. Reads the customer id from the
 * request-scoped {@link AssistantContext} — guests calling this tool see a
 * "you must log in first" response (enforced at the tool level so the
 * prompt can't bypass it).
 */
@Component
public class StoreOrderTrackingTool {

    private final OrderRepository orderRepository;

    public StoreOrderTrackingTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public static class OrderSummary {
        public Long id;
        public String orderNumber;
        public String status;
        public BigDecimal grandTotal;
        public String cargoCompany;
        public String cargoTrackingNo;
        public LocalDateTime createdAt;
        public String estimatedDelivery;
    }

    public static class OrderTrackingResult {
        public boolean requiresLogin;
        public String message;
        public List<OrderSummary> orders;
    }

    @Tool(description = "STORE: Giriş yapmış müşterinin son siparişlerini listeler. Misafir kullanıcı bu tool'u çağırırsa "
            + "requiresLogin=true döner — bu durumda LOGIN_PROMPT aksiyonu göster. Opsiyonel limit (1-10, varsayılan 5).")
    public OrderTrackingResult listMyOrders(
            @ToolParam(description = "Getirilecek maksimum sipariş sayısı (1-10)", required = false) Integer limit) {
        OrderTrackingResult result = new OrderTrackingResult();

        AssistantContext ctx = AssistantContextHolder.get();
        if (ctx == null || ctx.customerId() == null) {
            result.requiresLogin = true;
            result.message = "Sipariş bilgilerinize ulaşabilmem için üye girişi yapmanız gerekiyor.";
            result.orders = List.of();
            return result;
        }

        int safeLimit = limit != null ? Math.min(Math.max(limit, 1), 10) : 5;
        Page<Order> page = orderRepository.findByCustomerId(
                ctx.customerId(),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")));

        result.orders = page.getContent().stream().map(this::toSummary).toList();
        result.message = result.orders.isEmpty()
                ? "Henüz bir siparişiniz görünmüyor."
                : "Son " + result.orders.size() + " siparişiniz listelendi.";
        return result;
    }

    private OrderSummary toSummary(Order o) {
        OrderSummary s = new OrderSummary();
        s.id = o.getId();
        s.orderNumber = o.getOrderNumber();
        s.status = o.getStatus() != null ? turkishStatus(o.getStatus().name()) : null;
        s.grandTotal = o.getGrandTotal();
        s.cargoCompany = o.getCargoCompany() != null ? o.getCargoCompany().name() : null;
        s.cargoTrackingNo = o.getCargoTrackingNo();
        s.createdAt = o.getCreatedAt();
        if (o.getEstimatedDeliveryDate() != null) {
            s.estimatedDelivery = o.getEstimatedDeliveryDate().toString();
        }
        return s;
    }

    private String turkishStatus(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "PENDING_PAYMENT" -> "Ödeme bekliyor";
            case "PAID" -> "Ödeme alındı";
            case "PROCESSING" -> "Hazırlanıyor";
            case "SHIPPED" -> "Kargoya verildi";
            case "DELIVERED" -> "Teslim edildi";
            case "CANCELLED" -> "İptal edildi";
            case "REFUNDED" -> "İade edildi";
            default -> raw;
        };
    }
}
