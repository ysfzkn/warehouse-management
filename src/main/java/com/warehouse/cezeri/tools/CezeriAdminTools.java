package com.warehouse.cezeri.tools;

import com.warehouse.dto.AuditLogFilter;
import com.warehouse.dto.NotificationFilter;
import com.warehouse.entity.AuditLog;
import com.warehouse.entity.Notification;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.specification.AuditLogSpecifications;
import com.warehouse.service.NotificationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-only tools. IMPORTANT: This bean is only registered as tools for ADMIN users.
 */
@Component
public class CezeriAdminTools {

    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    public CezeriAdminTools(AuditLogRepository auditLogRepository,
                            NotificationService notificationService) {
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
    }

    @Tool(description = "ADMIN: Belirli bir entity için audit log kayıtlarını getir (entityType + entityId).")
    public List<AuditLog> auditByEntity(String entityType, Long entityId, Integer page, Integer size) {
        return getAuditLogs(entityType, entityId, page, size, auditLogRepository);
    }

    static List<AuditLog> getAuditLogs(String entityType, Long entityId, Integer page, Integer size, AuditLogRepository auditLogRepository) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 200;
        return auditLogRepository.findByEntity(entityType, entityId, PageRequest.of(safePage, safeSize)).getContent();
    }

    @Tool(description = "ADMIN: Depo bazlı audit log araması (opsiyonel arama metni ve tarih aralığı).")
    public Page<AuditLog> auditByWarehouse(Long warehouseId,
                                           Integer page,
                                           Integer size,
                                           String search,
                                           LocalDateTime startDate,
                                           LocalDateTime endDate) {
        return getAuditLogs(warehouseId, page, size, search, startDate, endDate, auditLogRepository);
    }

    static Page<AuditLog> getAuditLogs(Long warehouseId, Integer page, Integer size, String search, LocalDateTime startDate, LocalDateTime endDate, AuditLogRepository auditLogRepository) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 200;
        AuditLogFilter filter = AuditLogFilter.builder()
                .warehouseId(warehouseId)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return auditLogRepository.findAll(AuditLogSpecifications.withFilter(filter), PageRequest.of(safePage, safeSize));
    }

    @Tool(description = "ADMIN: Okunmamış bildirimleri listeler.")
    public List<Notification> unreadNotifications() {
        return notificationService.unread();
    }

    @Tool(description = "ADMIN: Son bildirimleri listeler.")
    public List<Notification> recentNotifications() {
        return notificationService.recent();
    }

    @Tool(description = "ADMIN: Bildirimlerde arama/filtreleme (warehouse/entityType/search/date range) + sayfalama.")
    public Page<Notification> searchNotifications(Integer page,
                                                  Integer size,
                                                  Long warehouseId,
                                                  String entityType,
                                                  String search,
                                                  LocalDateTime startDate,
                                                  LocalDateTime endDate) {
        return getNotifications(page, size, warehouseId, entityType, search, startDate, endDate, notificationService);
    }

    static Page<Notification> getNotifications(Integer page, Integer size, Long warehouseId, String entityType, String search, LocalDateTime startDate, LocalDateTime endDate, NotificationService notificationService) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;
        NotificationFilter filter = NotificationFilter.builder()
                .warehouseId(warehouseId)
                .entityType(entityType)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return notificationService.search(filter, PageRequest.of(safePage, safeSize));
    }
}


