package com.warehouse.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogFilter {
    private Long warehouseId;
    private String search;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}

