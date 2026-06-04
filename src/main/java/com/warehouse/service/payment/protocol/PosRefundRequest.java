package com.warehouse.service.payment.protocol;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class PosRefundRequest {
    private String transactionId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String ip;
}
