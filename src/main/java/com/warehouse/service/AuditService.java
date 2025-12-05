package com.warehouse.service;

import com.warehouse.dto.AuditMetadata;
import com.warehouse.entity.AuditLog;
import com.warehouse.enums.AuditAction;

import java.util.List;

/**
 * Service interface for managing audit logs.
 */
public interface AuditService {

    void log(AuditAction action, String entityType, Long entityId, String username, String details);

    void log(AuditAction action, String entityType, Long entityId, String username, String details, AuditMetadata metadata);

    List<AuditLog> recent();
}
