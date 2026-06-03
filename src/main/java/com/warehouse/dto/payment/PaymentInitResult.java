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
    /** Active gateway provider: "IYZICO", "PAYTR", "NESTPAY", etc.
     *  The frontend renders the brand name/logo based on this value. */
    private String providerName;
    /** Display name of the gateway: "iyzico", "PayTR", "İş Bankası NestPay", etc. */
    private String providerDisplayName;
}
