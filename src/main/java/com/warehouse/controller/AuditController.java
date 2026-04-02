package com.warehouse.controller;

import com.warehouse.dto.AuditLogFilter;
import com.warehouse.entity.AuditLog;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.specification.AuditLogSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
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

    @GetMapping("/warehouse/{warehouseId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<AuditLog>> byWarehouse(@PathVariable Long warehouseId,
                                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                                      @RequestParam(name = "size", defaultValue = "200") int size,
                                                      @RequestParam(name = "search", required = false) String search,
                                                      @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                                      @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        AuditLogFilter filter = AuditLogFilter.builder()
                .warehouseId(warehouseId)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        Page<AuditLog> auditPage = auditLogRepository.findAll(AuditLogSpecifications.withFilter(filter), pageable);
        return ResponseEntity.ok(auditPage);
    }
}


