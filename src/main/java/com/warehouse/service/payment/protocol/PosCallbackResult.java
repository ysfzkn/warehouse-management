package com.warehouse.service.payment.protocol;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data @Builder
public class PosCallbackResult {
    private boolean success;
    private String transactionId;
    private String orderId;
    private BigDecimal paidAmount;
    private int installmentCount;
    private String cardLastFour;
    private String cardType;
    private String cardAssociation;
    private boolean threeDSecure;
    private String mdStatus;        // 3D authentication status
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> rawResponse;
}
