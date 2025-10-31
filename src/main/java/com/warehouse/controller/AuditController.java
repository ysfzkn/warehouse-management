package com.warehouse.controller;

import com.warehouse.entity.AuditLog;
import com.warehouse.repository.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> byEntity(@RequestParam("entityType") String entityType,
                                                   @RequestParam("entityId") Long entityId,
                                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                                   @RequestParam(name = "size", defaultValue = "200") int size) {
        // Simple pagination in memory using repository query result
        List<AuditLog> all = auditLogRepository.findByEntity(entityType, entityId);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<AuditLog> content = all.subList(from, to);
        return ResponseEntity.ok(content);
    }
}


