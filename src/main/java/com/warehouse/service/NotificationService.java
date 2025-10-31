package com.warehouse.service;

import com.warehouse.entity.Notification;
import com.warehouse.repository.NotificationRepository;
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
}


