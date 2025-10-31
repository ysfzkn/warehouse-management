package com.warehouse.service;

import com.warehouse.entity.Notification;
import com.warehouse.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification create(String title, String message) {
        Notification n = new Notification();
        n.setTitle(title);
        n.setMessage(message);
        return notificationRepository.save(n);
    }

    public Notification create(String title, String message, String entityType, Long entityId) {
        Notification n = new Notification();
        n.setTitle(title);
        n.setMessage(message);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        return notificationRepository.save(n);
    }

    public void markRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional(readOnly = true)
    public List<Notification> unread() {
        return notificationRepository.findByReadFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Notification> recent() {
        return notificationRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Notification> page(Pageable pageable) {
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}


