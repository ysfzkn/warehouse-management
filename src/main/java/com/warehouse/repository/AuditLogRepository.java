package com.warehouse.repository;

import com.warehouse.entity.AuditLog;
import com.warehouse.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    @Query("select a from AuditLog a where a.entityType = :entityType and a.entityId = :entityId order by a.createdAt desc")
    List<AuditLog> findByEntity(String entityType, Long entityId);
}


