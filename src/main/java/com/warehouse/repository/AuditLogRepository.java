package com.warehouse.repository;

import com.warehouse.entity.AuditLog;
import com.warehouse.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    @Query("select a from AuditLog a where a.entityType = :entityType and a.entityId = :entityId order by a.createdAt desc")
    Page<AuditLog> findByEntity(String entityType, Long entityId, Pageable pageable);

    /**
     * Recent manual stock removals, newest first — the candidate pool for the duplicate-delivery
     * check. Names are matched in Java rather than SQL: the same customer is written with and
     * without Turkish letters and appears mid-sentence inside {@code note}, neither of which a
     * portable {@code LIKE} can handle. The date window plus the page size keep the pool small.
     */
    @Query("select a from AuditLog a where a.action = :action and a.createdAt >= :since " +
           "and (a.customerName is not null or a.note is not null) order by a.createdAt desc")
    List<AuditLog> findRecentByAction(@Param("action") AuditAction action,
                                      @Param("since") java.time.LocalDateTime since,
                                      Pageable pageable);
}
