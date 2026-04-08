package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data @Builder
public class PaymentCallbackResult {
    private boolean success;
    private String token;
    private String paymentId;
    private BigDecimal paidPrice;
    private int installmentCount;
    private String cardLastFour;
    private String cardType;
    private String cardAssociation;
    private String cardFamily;
    private String cardBankName;
    private boolean threeDSecure;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> rawResponse;
}
