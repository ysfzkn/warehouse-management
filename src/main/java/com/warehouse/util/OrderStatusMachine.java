package com.warehouse.util;

import com.warehouse.enums.OrderStatus;

import java.util.*;

/**
 * Order status state machine — defines valid transitions.
 * Prevents illogical status changes (e.g., DELIVERED → PENDING_PAYMENT).
 */
public final class OrderStatusMachine {

    private OrderStatusMachine() {}

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.CANCELLED),
        OrderStatus.PAID, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
        OrderStatus.PREPARING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
        OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED),
        OrderStatus.DELIVERED, Set.of(OrderStatus.RETURN_REQUESTED, OrderStatus.REFUNDED),
        OrderStatus.RETURN_REQUESTED, Set.of(OrderStatus.RETURNED, OrderStatus.DELIVERED),
        OrderStatus.RETURNED, Set.of(OrderStatus.REFUNDED),
        OrderStatus.CANCELLED, Set.of(),
        OrderStatus.REFUNDED, Set.of()
    );

    /** Labels for each status in Turkish */
    private static final Map<OrderStatus, String> LABELS = Map.of(
        OrderStatus.PENDING_PAYMENT, "Ödeme Bekliyor",
        OrderStatus.PAID, "Ödendi",
        OrderStatus.PREPARING, "Hazırlanıyor",
        OrderStatus.SHIPPED, "Kargoda",
        OrderStatus.DELIVERED, "Teslim Edildi",
        OrderStatus.CANCELLED, "İptal Edildi",
        OrderStatus.RETURN_REQUESTED, "İade Talebi",
        OrderStatus.RETURNED, "İade Edildi",
        OrderStatus.REFUNDED, "İade Ödemesi Yapıldı"
    );

    public static boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) return false;
        Set<OrderStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static List<OrderStatus> getAllowedTransitions(OrderStatus current) {
        if (current == null) return List.of();
        Set<OrderStatus> allowed = TRANSITIONS.get(current);
        return allowed != null ? new ArrayList<>(allowed) : List.of();
    }

    public static String getLabel(OrderStatus status) {
        return LABELS.getOrDefault(status, status != null ? status.name() : "");
    }

    public static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED;
    }

    public static boolean isDoorPayment(String paymentMethod) {
        return "DOOR_CASH".equals(paymentMethod) || "DOOR_CARD".equals(paymentMethod);
    }
}
