package com.warehouse.service.payment.protocol;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data @Builder
public class PosRefundResult {
    private boolean success;
    private BigDecimal refundedAmount;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> rawResponse;
}
