package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data @Builder
public class PaymentInitResult {
    private boolean success;
    private String htmlContent;
    private String token;
    private String bankTransferReference;
    private Map<String, String> bankDetails;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> rawResponse;
}
