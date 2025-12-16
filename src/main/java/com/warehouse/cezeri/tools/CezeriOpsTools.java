package com.warehouse.cezeri.tools;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.AuditLog;
import com.warehouse.entity.Notification;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.specification.AuditLogSpecifications;
import com.warehouse.dto.AuditLogFilter;
import com.warehouse.dto.NotificationFilter;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockRequestService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.warehouse.cezeri.tools.CezeriAdminTools.getAuditLogs;
import static com.warehouse.cezeri.tools.CezeriAdminTools.getNotifications;

/**
 * Legacy tool container (kept for backward compatibility).
 * Use {@link CezeriUserTools} and {@link CezeriAdminTools} instead.
 */
@Component
public class CezeriOpsTools {

    private final StockRequestService stockRequestService;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    public CezeriOpsTools(StockRequestService stockRequestService,
                          AuditLogRepository auditLogRepository,
                          NotificationService notificationService) {
        this.stockRequestService = stockRequestService;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
    }

    @Tool(description = "List current user's stock requests, optionally filtered by status (PENDING/APPROVED/REJECTED).")
    public List<StockRequestDto> myStockRequests(String status) {
        StockRequestStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = StockRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (Exception ignored) { }
        }
        return stockRequestService.getRequestsForCurrentUser(parsed);
    }

    @Tool(description = "Fetch audit logs for a specific entity (entityType + entityId).")
    public List<AuditLog> auditByEntity(String entityType, Long entityId, Integer page, Integer size) {
        return getAuditLogs(entityType, entityId, page, size, auditLogRepository);
    }

    @Tool(description = "Fetch audit logs for a warehouse with optional search and date range filters.")
    public Page<AuditLog> auditByWarehouse(Long warehouseId,
                                           Integer page,
                                           Integer size,
                                           String search,
                                           LocalDateTime startDate,
                                           LocalDateTime endDate) {
        return getAuditLogs(warehouseId, page, size, search, startDate, endDate, auditLogRepository);
    }

    @Tool(description = "List unread notifications for the current user.")
    public List<Notification> unreadNotifications() {
        return notificationService.unread();
    }

    @Tool(description = "List recent notifications for the current user.")
    public List<Notification> recentNotifications() {
        return notificationService.recent();
    }

    @Tool(description = "Search notifications with optional filters (warehouse/entityType/search/date range) and pagination.")
    public Page<Notification> searchNotifications(Integer page,
                                                  Integer size,
                                                  Long warehouseId,
                                                  String entityType,
                                                  String search,
                                                  LocalDateTime startDate,
                                                  LocalDateTime endDate) {
        return getNotifications(page, size, warehouseId, entityType, search, startDate, endDate, notificationService);
    }
}


