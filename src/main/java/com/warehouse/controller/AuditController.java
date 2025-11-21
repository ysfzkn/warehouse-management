package com.warehouse.controller;

import com.warehouse.entity.AuditLog;
import com.warehouse.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    private static final int MAX_PAGE_SIZE = 500;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AuditLog>> byEntity(@RequestParam("entityType") String entityType,
                                                   @RequestParam("entityId") Long entityId,
                                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                                   @RequestParam(name = "size", defaultValue = "200") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<AuditLog> auditPage = auditLogRepository.findByEntity(entityType, entityId, pageable);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(auditPage.getTotalElements()))
                .body(auditPage.getContent());
    }
}


