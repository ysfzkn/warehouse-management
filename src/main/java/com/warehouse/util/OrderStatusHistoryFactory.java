package com.warehouse.util;

import com.warehouse.entity.Order;
import com.warehouse.entity.OrderStatusHistory;
import com.warehouse.enums.OrderStatus;

public final class OrderStatusHistoryFactory {

    private OrderStatusHistoryFactory() {}

    public static OrderStatusHistory create(Order order, OrderStatus oldStatus, OrderStatus newStatus,
                                             String changedBy, String changeSource, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus != null ? oldStatus.name() : null);
        history.setNewStatus(newStatus.name());
        history.setChangedBy(changedBy);
        history.setChangeSource(changeSource);
        history.setNote(note);
        return history;
    }

    public static OrderStatusHistory create(Order order, String oldStatus, String newStatus,
                                             String changedBy, String changeSource, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setChangeSource(changeSource);
        history.setNote(note);
        return history;
    }
}
