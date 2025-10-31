package com.warehouse.controller;

import com.warehouse.entity.Notification;
import com.warehouse.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> unread() {
        return ResponseEntity.ok(notificationService.unread());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Notification>> recent() {
        return ResponseEntity.ok(notificationService.recent());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}


