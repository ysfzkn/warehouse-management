package com.warehouse.service.impl;

import com.warehouse.entity.AuditLog;
import com.warehouse.enums.AuditAction;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of AuditService for managing audit logs.
 */
@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void log(AuditAction action, String entityType, Long entityId, String username, String details) {
        logger.debug("Creating audit log: action={}, entityType={}, entityId={}, username={}", 
                action, entityType, entityId, username);
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setUsername(username);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> recent() {
        logger.debug("Fetching recent audit logs");
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }
}

