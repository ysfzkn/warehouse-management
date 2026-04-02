package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class RefundRequest {
    private String paymentTransactionId;
    private String conversationId;
    private BigDecimal amount;
    private String currency;
    private String ip;
    private String reason;
}
