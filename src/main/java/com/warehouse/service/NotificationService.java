package com.warehouse.service;

import com.warehouse.entity.Notification;
import com.warehouse.dto.NotificationFilter;
import com.warehouse.dto.NotificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing notifications.
 */
public interface NotificationService {

    Notification create(NotificationRequest request);

    Notification create(String title, String message);

    Notification create(String title, String message, String entityType, Long entityId);

    void markRead(Long id);

    List<Notification> unread();

    List<Notification> recent();

    Page<Notification> page(Pageable pageable);

    Page<Notification> search(NotificationFilter filter, Pageable pageable);

    /**
     * Returns the total number of unread notifications.
     */
    long unreadCount();

    /** Unread count for a single workspace domain (WMS / ECOM); null/blank = all. */
    long unreadCount(String domain);
}
