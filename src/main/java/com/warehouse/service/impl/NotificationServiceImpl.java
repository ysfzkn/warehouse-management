package com.warehouse.service.impl;

import com.warehouse.dto.NotificationFilter;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.entity.Notification;
import com.warehouse.repository.NotificationRepository;
import com.warehouse.repository.specification.NotificationSpecifications;
import com.warehouse.service.NotificationService;
import com.warehouse.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import com.warehouse.service.SsePushService;

/**
 * Implementation of NotificationService for managing notifications.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final SsePushService ssePushService;

    public NotificationServiceImpl(NotificationRepository notificationRepository, SsePushService ssePushService) {
        this.notificationRepository = notificationRepository;
        this.ssePushService = ssePushService;
    }

    @Override
    public Notification create(NotificationRequest request) {
        logger.debug("Creating notification with metadata: {}", request != null ? request.getTitle() : "null");
        Notification notification = new Notification();
        if (request != null) {
            notification.setTitle(request.getTitle());
            notification.setMessage(request.getMessage());
            notification.setEntityType(request.getEntityType());
            notification.setEntityId(request.getEntityId());
            String actor = StringUtils.hasText(request.getActor())
                    ? request.getActor()
                    : CurrentUser.usernameOrSystem();
            notification.setActor(actor);
            notification.setWarehouseId(request.getWarehouseId());
            notification.setWarehouseName(request.getWarehouseName());
            notification.setSourceWarehouseId(request.getSourceWarehouseId());
            notification.setSourceWarehouseName(request.getSourceWarehouseName());
            notification.setDestinationWarehouseId(request.getDestinationWarehouseId());
            notification.setDestinationWarehouseName(request.getDestinationWarehouseName());
            notification.setProductId(request.getProductId());
            notification.setProductName(request.getProductName());
            notification.setProductSku(request.getProductSku());
            notification.setQuantity(request.getQuantity());
        } else {
            notification.setActor(CurrentUser.usernameOrSystem());
        }
        Notification saved = notificationRepository.save(notification);
        logger.debug("Notification created with id: {}", saved.getId());
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after notification create. notifId={}", saved.getId());
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after notification create. notifId={}", saved.getId(), e);
        }
        return saved;
    }

    @Override
    public Notification create(String title, String message) {
        return create(NotificationRequest.builder()
                .title(title)
                .message(message)
                .actor(CurrentUser.usernameOrSystem())
                .build());
    }

    @Override
    public Notification create(String title, String message, String entityType, Long entityId) {
        return create(NotificationRequest.builder()
                .title(title)
                .message(message)
                .entityType(entityType)
                .entityId(entityId)
                .actor(CurrentUser.usernameOrSystem())
                .build());
    }

    @Override
    public void markRead(Long id) {
        logger.debug("Marking notification as read: {}", id);
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
            try {
                ssePushService.broadcastCounts();
                logger.debug("SSE counts broadcasted after notification markRead. notifId={}", id);
            } catch (Exception e) {
                logger.warn("SSE broadcast failed after notification markRead. notifId={}", id, e);
            }
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

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> search(NotificationFilter filter, Pageable pageable) {
        logger.debug("Searching notifications with filters");
        return notificationRepository.findAll(NotificationSpecifications.withFilter(filter), pageable);
    }

    /**
     * Returns unread count using a lightweight repository count query.
     * Cached briefly to reduce database load during bursts.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "counts", key = "'unread'", unless = "#result == null")
    public long unreadCount() {
        return notificationRepository.countByReadFalse();
    }
}

