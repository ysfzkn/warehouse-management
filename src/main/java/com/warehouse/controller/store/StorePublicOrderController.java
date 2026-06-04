package com.warehouse.controller.store;

import com.warehouse.dto.store.PublicOrderTrackingDto;
import com.warehouse.entity.CargoProvider;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.OrderStatusHistory;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public order tracking endpoints.
 * Customers can query their orders by order number + email without logging in.
 */
@RestController
@RequestMapping("/api/store/public/orders")
@RequiredArgsConstructor
public class StorePublicOrderController {

    private static final Logger logger = LoggerFactory.getLogger(StorePublicOrderController.class);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final CargoProviderRepository cargoProviderRepository;

    /**
     * Order tracking: queries by order number + email.
     * Returns only public tracking data (contains no sensitive information).
     */
    @PostMapping("/track")
    @Transactional(readOnly = true)
    public ResponseEntity<?> trackOrder(@RequestBody Map<String, String> body) {
        String orderNumber = body.get("orderNumber");
        String email = body.get("email");

        if (orderNumber == null || orderNumber.isBlank() || email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sipariş numarası ve e-posta zorunludur."));
        }

        Optional<Order> orderOpt = orderRepository.findByOrderNumberAndCustomerEmail(
                orderNumber.trim(), email.trim().toLowerCase());

        if (orderOpt.isEmpty()) {
            // Security: don't give details, prevent enumeration attacks
            logger.info("Sipariş takip başarısız: orderNumber={}, email={}", orderNumber, email);
            return ResponseEntity.status(404).body(Map.of(
                "error", "Bu bilgilere ait sipariş bulunamadı. Sipariş numarası ve e-posta adresinizi kontrol edin."
            ));
        }

        Order order = orderOpt.get();
        PublicOrderTrackingDto dto = toPublicDto(order);
        return ResponseEntity.ok(dto);
    }

    // === Private helpers ===

    private PublicOrderTrackingDto toPublicDto(Order order) {
        // Status history
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        List<PublicOrderTrackingDto.StatusHistoryItem> historyItems = history.stream()
            .map(h -> PublicOrderTrackingDto.StatusHistoryItem.builder()
                .status(h.getNewStatus())
                .statusLabel(statusLabel(h.getNewStatus()))
                .changedAt(h.getCreatedAt())
                .note(h.getNote())
                .build())
            .toList();

        // Item count
        int itemCount = 0;
        try {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            itemCount = items.stream().mapToInt(OrderItem::getQuantity).sum();
        } catch (Exception ignored) {}

        // Cargo tracking URL
        String trackingUrl = buildTrackingUrl(order);

        // Masked customer name (e.g., "Ahmet Y***")
        String maskedName = maskCustomerName(order);

        return PublicOrderTrackingDto.builder()
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .statusLabel(statusLabel(order.getStatus().name()))
            .createdAt(order.getCreatedAt())
            .estimatedDeliveryDate(order.getEstimatedDeliveryDate())
            .actualDeliveryDate(order.getActualDeliveryDate())
            .cargoCompany(order.getCargoCompany() != null ? order.getCargoCompany().name() : null)
            .cargoProviderName(order.getCargoProviderName())
            .cargoTrackingNo(order.getCargoTrackingNo())
            .cargoTrackingUrl(trackingUrl)
            .grandTotal(order.getGrandTotal())
            .itemCount(itemCount)
            .maskedCustomerName(maskedName)
            .statusHistory(historyItems)
            .build();
    }

    private String buildTrackingUrl(Order order) {
        if (order.getCargoTrackingNo() == null || order.getCargoTrackingNo().isBlank()) {
            return null;
        }
        if (order.getCargoProviderId() == null) {
            return null;
        }
        try {
            CargoProvider provider = cargoProviderRepository.findById(order.getCargoProviderId()).orElse(null);
            if (provider == null || provider.getTrackingUrlTemplate() == null) return null;
            return provider.getTrackingUrlTemplate().replace("{trackingNo}", order.getCargoTrackingNo());
        } catch (Exception e) {
            return null;
        }
    }

    private String maskCustomerName(Order order) {
        try {
            if (order.getCustomer() == null) return "";
            String first = order.getCustomer().getFirstName();
            String last = order.getCustomer().getLastName();
            if (first == null) first = "";
            if (last == null) last = "";
            String maskedLast = last.isEmpty() ? "" : last.charAt(0) + "***";
            return (first + " " + maskedLast).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING_PAYMENT" -> "Ödeme Bekliyor";
            case "PAID" -> "Ödeme Alındı";
            case "PREPARING" -> "Hazırlanıyor";
            case "SHIPPED" -> "Kargoda";
            case "DELIVERED" -> "Teslim Edildi";
            case "CANCELLED" -> "İptal Edildi";
            case "RETURN_REQUESTED" -> "İade Talep Edildi";
            case "RETURNED" -> "İade Edildi";
            case "REFUNDED" -> "Para İadesi Yapıldı";
            default -> status;
        };
    }
}
