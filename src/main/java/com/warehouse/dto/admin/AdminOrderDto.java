package com.warehouse.dto.admin;

import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.OrderChannel;
import com.warehouse.enums.ManualPaymentState;
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
    private OrderChannel orderChannel;
    private String channelReference;
    private ManualPaymentState manualPaymentState;
    private LocalDateTime paymentDueAt;
    private LocalDateTime paymentReminderAt;
    private String deliveryMethod;
    private String cargoCompany;
    private String cargoTrackingNo;
    private int itemCount;
    private LocalDateTime createdAt;
}
