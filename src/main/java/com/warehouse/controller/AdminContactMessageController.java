package com.warehouse.controller;

import com.warehouse.entity.ContactMessage;
import com.warehouse.enums.ContactMessageStatus;
import com.warehouse.repository.ContactMessageRepository;
import com.warehouse.util.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/contact-messages")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminContactMessageController {

    private final ContactMessageRepository repository;

    public AdminContactMessageController(ContactMessageRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        ContactMessageStatus statusFilter = parseStatus(status);
        Page<ContactMessage> result = (statusFilter != null)
                ? repository.findByStatusOrderByCreatedAtDesc(statusFilter, PageRequest.of(page, size))
                : repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        List<Map<String, Object>> dtos = result.getContent().stream().map(this::toDto).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", dtos);
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("number", result.getNumber());
        response.put("size", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(Map.of(
                "NEW", repository.countByStatus(ContactMessageStatus.NEW),
                "READ", repository.countByStatus(ContactMessageStatus.READ),
                "ARCHIVED", repository.countByStatus(ContactMessageStatus.ARCHIVED)
        ));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id).map(msg -> {
            ContactMessageStatus newStatus = parseStatus(body.getOrDefault("status", ""));
            if (newStatus == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Geçersiz durum."));
            }
            msg.setStatus(newStatus);
            if (newStatus == ContactMessageStatus.READ && msg.getReadAt() == null) {
                msg.setReadAt(LocalDateTime.now());
                msg.setReadBy(CurrentUser.usernameOrSystem());
            }
            repository.save(msg);
            return ResponseEntity.ok(Map.of("message", "Durum güncellendi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(ContactMessage m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("name", m.getName());
        dto.put("email", m.getEmail());
        dto.put("phone", m.getPhone());
        dto.put("subject", m.getSubject());
        dto.put("message", m.getMessage());
        dto.put("status", m.getStatus() != null ? m.getStatus().name() : null);
        dto.put("emailSent", m.isEmailSent());
        dto.put("ipAddress", m.getIpAddress());
        dto.put("createdAt", m.getCreatedAt());
        dto.put("readAt", m.getReadAt());
        dto.put("readBy", m.getReadBy());
        return dto;
    }

    private static ContactMessageStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return ContactMessageStatus.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
