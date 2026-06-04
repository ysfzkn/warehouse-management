package com.warehouse.controller;

import com.warehouse.dto.NotificationFilter;
import com.warehouse.entity.Notification;
import com.warehouse.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
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
                                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                                   @RequestParam(name = "warehouseId", required = false) Long warehouseId,
                                                   @RequestParam(name = "entityType", required = false) String entityType,
                                                   @RequestParam(name = "search", required = false) String search,
                                                   @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                                   @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        NotificationFilter filter = NotificationFilter.builder()
                .warehouseId(warehouseId)
                .entityType(entityType)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        Page<Notification> p = notificationService.search(filter, PageRequest.of(page, Math.min(Math.max(size, 1), 1000)));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(p.getTotalElements()))
                .body(p.getContent());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Notification>> search(@RequestParam(name = "size", defaultValue = "50") int size,
                                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "warehouseId", required = false) Long warehouseId,
                                                     @RequestParam(name = "entityType", required = false) String entityType,
                                                     @RequestParam(name = "search", required = false) String search,
                                                     @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                                     @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        NotificationFilter filter = NotificationFilter.builder()
                .warehouseId(warehouseId)
                .entityType(entityType)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        Page<Notification> p = notificationService.search(filter, PageRequest.of(page, safeSize));
        return ResponseEntity.ok(p);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}


