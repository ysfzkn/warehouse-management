package com.warehouse.controller.store;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/orders")
public class StoreOrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final JwtService jwtService;

    public StoreOrderController(OrderRepository orderRepo, OrderItemRepository orderItemRepo, JwtService jwtService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.jwtService = jwtService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> myOrders(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.ok(new PagedResponse<>(java.util.List.of(), 0, size, 0, 0, true, true));

        Page<Order> orders = orderRepo.findByCustomerId(customerId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<Map<String, Object>> dtos = orders.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(new PagedResponse<>(dtos, orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages(), orders.isFirst(), orders.isLast()));
    }

    @GetMapping("/{orderNumber}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> orderDetail(HttpServletRequest request, @PathVariable String orderNumber) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.notFound().build();

        return orderRepo.findByOrderNumber(orderNumber)
                .filter(o -> o.getCustomer() != null && o.getCustomer().getId().equals(customerId))
                .map(o -> {
                    Map<String, Object> dto = toDto(o);
                    dto.put("shippingAddress", o.getShippingAddressSnapshot());
                    dto.put("billingAddress", o.getBillingAddressSnapshot());
                    dto.put("customerNote", o.getCustomerNote());
                    dto.put("cargoCompany", o.getCargoCompany());
                    dto.put("cargoTrackingNo", o.getCargoTrackingNo());
                    // Items
                    List<Map<String, Object>> items = new ArrayList<>();
                    try {
                        for (OrderItem item : o.getItems()) {
                            Map<String, Object> it = new LinkedHashMap<>();
                            Map<String, Object> snap = item.getProductSnapshot();
                            it.put("productName", snap != null ? snap.getOrDefault("name", "—") : "—");
                            it.put("productSku", snap != null ? snap.getOrDefault("sku", "") : "");
                            it.put("quantity", item.getQuantity());
                            it.put("unitPrice", item.getUnitPrice());
                            it.put("lineTotal", item.getLineTotal());
                            items.add(it);
                        }
                    } catch (Exception ignored) {}
                    dto.put("items", items);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(Order o) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", o.getId());
        dto.put("orderNumber", o.getOrderNumber());
        dto.put("status", o.getStatus() != null ? o.getStatus().name() : null);
        dto.put("grandTotal", o.getGrandTotal());
        dto.put("subtotal", o.getSubtotal());
        dto.put("shippingCost", o.getShippingCost());
        dto.put("discountAmount", o.getDiscountAmount());
        dto.put("paymentMethod", o.getPaymentMethod());
        int itemCount = 0;
        try { if (o.getItems() != null) itemCount = o.getItems().size(); } catch (Exception ignored) {}
        dto.put("itemCount", itemCount);
        dto.put("cargoTrackingNo", o.getCargoTrackingNo());
        dto.put("cargoCompany", o.getCargoCompany() != null ? o.getCargoCompany().name() : null);
        dto.put("createdAt", o.getCreatedAt());
        return dto;
    }

    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else {
                // Try cookie
                if (request.getCookies() != null) {
                    for (var c : request.getCookies()) {
                        if ("access_token".equals(c.getName())) { token = c.getValue(); break; }
                    }
                }
            }
            if (token == null) return null;
            return jwtService.extractCustomerId(token);
        } catch (Exception e) { return null; }
    }
}
