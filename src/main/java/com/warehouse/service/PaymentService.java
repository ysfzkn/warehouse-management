package com.warehouse.service;

import com.warehouse.dto.payment.*;
import java.math.BigDecimal;
import java.util.Map;

public interface PaymentService {
    /**
     * Starts a payment for an order.
     *
     * @param caller proof that the requester is entitled to pay for this order — either
     *               the authenticated customer who owns it, or the one-time token issued
     *               at checkout. Required: the endpoint is public, so without this any
     *               caller could act on any order id.
     */
    PaymentInitResult initializePayment(Long orderId, String paymentMethod, int installmentCount,
                                         String ipAddress, String idempotencyKey,
                                         PaymentCaller caller);
    PaymentCallbackResult handlePaymentCallback(Map<String, String> params);
    /**
     * Bank transfer confirmation — to prevent accidental confirmation with the wrong
     * amount/date, the actual deposit details are taken from the admin and validated
     * against the order amount.
     */
    void confirmBankTransfer(Long orderId, BigDecimal paidAmount,
                              java.time.LocalDateTime paidAt,
                              String confirmedBy, String receiptNote);

    /** Bank transfer rejection — wrong amount, customer cancelled, etc. */
    void rejectBankTransfer(Long orderId, String reason, String rejectedBy);
    RefundResult initiateRefund(Long orderId, BigDecimal amount, String reason, String ipAddress);
    PaymentStatusResult getPaymentStatus(Long paymentId);
    InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price);
    com.warehouse.entity.PaymentTransaction findTransactionByToken(String token);
}
