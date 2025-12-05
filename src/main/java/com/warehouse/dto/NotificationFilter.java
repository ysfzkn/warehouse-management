package com.warehouse.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationFilter {
    private Long warehouseId;
    private String entityType;
    private String search;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}

