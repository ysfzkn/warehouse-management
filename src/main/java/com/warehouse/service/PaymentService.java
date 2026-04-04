package com.warehouse.service;

import com.warehouse.dto.payment.*;
import java.math.BigDecimal;
import java.util.Map;

public interface PaymentService {
    PaymentInitResult initializePayment(Long orderId, String paymentMethod, int installmentCount,
                                         String ipAddress, String idempotencyKey);
    PaymentCallbackResult handlePaymentCallback(Map<String, String> params);
    void confirmBankTransfer(Long orderId, String confirmedBy);
    RefundResult initiateRefund(Long orderId, BigDecimal amount, String reason, String ipAddress);
    PaymentStatusResult getPaymentStatus(Long paymentId);
    InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price);
    com.warehouse.entity.PaymentTransaction findTransactionByToken(String token);
}
