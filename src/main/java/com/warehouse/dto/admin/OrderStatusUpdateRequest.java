package com.warehouse.dto.admin;

import com.warehouse.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusUpdateRequest {
    @NotNull private OrderStatus status;
    private String note;
}
