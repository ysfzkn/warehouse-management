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

    // ── For the "user info" requirements of PayTR + other POS protocols ──
    // In the PayTR production environment, if user_name/user_address/user_phone are sent
    // empty, some banks reject the request (fraud detection). These fields are populated
    // by VirtualPosGateway from the PaymentInitRequest.
    private String customerName;
    private String customerPhone;
    private String customerAddress;
}
