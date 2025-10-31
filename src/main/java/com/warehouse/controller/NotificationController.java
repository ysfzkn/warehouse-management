package com.warehouse.controller;

import com.warehouse.entity.Notification;
import com.warehouse.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping
    public ResponseEntity<List<Notification>> list(@RequestParam(name = "size", defaultValue = "200") int size,
                                                   @RequestParam(name = "page", defaultValue = "0") int page) {
        Page<Notification> p = notificationService.page(PageRequest.of(page, Math.min(Math.max(size, 1), 1000)));
        return ResponseEntity.ok(p.getContent());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}


