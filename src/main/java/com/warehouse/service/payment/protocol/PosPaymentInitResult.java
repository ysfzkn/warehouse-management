package com.warehouse.service.payment.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data @Builder
public class PosPaymentInitResult {
    private boolean success;
    private String htmlContent;     // Auto-submit form for 3D redirect
    private String transactionId;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> rawResponse;
}
