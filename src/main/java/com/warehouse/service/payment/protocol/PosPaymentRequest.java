package com.warehouse.service.payment.protocol;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class PosPaymentRequest {
    private String orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String currency;
    private int installmentCount;
    private String customerIp;
    private String customerEmail;
    private String okUrl;    // Success callback URL
    private String failUrl;  // Failure callback URL
}
