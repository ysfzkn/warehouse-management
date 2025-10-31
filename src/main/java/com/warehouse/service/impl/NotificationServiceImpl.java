package com.warehouse.service.impl;

import com.warehouse.entity.Notification;
import com.warehouse.repository.NotificationRepository;
import com.warehouse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of NotificationService for managing notifications.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification create(String title, String message) {
        logger.debug("Creating notification: {}", title);
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        Notification saved = notificationRepository.save(notification);
        logger.debug("Notification created with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Notification create(String title, String message, String entityType, Long entityId) {
        logger.debug("Creating notification: {} for entity {}:{}", title, entityType, entityId);
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        Notification saved = notificationRepository.save(notification);
        logger.debug("Notification created with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void markRead(Long id) {
        logger.debug("Marking notification as read: {}", id);
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
            logger.debug("Notification marked as read: {}", id);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> unread() {
        logger.debug("Fetching unread notifications");
        return notificationRepository.findByReadFalseOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> recent() {
        logger.debug("Fetching recent notifications");
        return notificationRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> page(Pageable pageable) {
        logger.debug("Fetching notifications page");
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

