package com.warehouse.dto.admin;

import com.warehouse.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class AdminOrderDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private OrderStatus status;
    private BigDecimal grandTotal;
    private String paymentMethod;
    private String cargoCompany;
    private String cargoTrackingNo;
    private int itemCount;
    private LocalDateTime createdAt;
}
