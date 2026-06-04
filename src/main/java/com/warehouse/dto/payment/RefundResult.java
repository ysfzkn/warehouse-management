package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data @Builder
public class RefundResult {
    private boolean success;
    private String refundId;
    private BigDecimal refundedAmount;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> rawResponse;
}
