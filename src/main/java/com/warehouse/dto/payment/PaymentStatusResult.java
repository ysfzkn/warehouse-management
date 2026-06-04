package com.warehouse.dto.payment;

import com.warehouse.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class PaymentStatusResult {
    private Long paymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String orderNumber;
    private String errorMessage;
}
