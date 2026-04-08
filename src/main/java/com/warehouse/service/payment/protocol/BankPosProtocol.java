package com.warehouse.service.payment.protocol;

import com.warehouse.entity.PaymentGatewayConfig;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Protocol handler interface for direct bank POS integrations.
 * Each bank protocol (NestPay, GVP, etc.) implements this interface.
 */
public interface BankPosProtocol {

    /** Protocol name: "NESTPAY", "GVP" */
    String getProtocolName();

    /** Initialize 3D Secure payment — returns HTML form for bank redirect */
    PosPaymentInitResult initialize3DPayment(PaymentGatewayConfig config, PosPaymentRequest request);

    /** Handle 3D callback from bank — verifies hash and processes result */
    PosCallbackResult handle3DCallback(PaymentGatewayConfig config, Map<String, String> params);

    /**
     * CRITICAL: Verify callback hash before any processing.
     * Must be called BEFORE handle3DCallback to prevent fraud.
     */
    boolean verifyCallbackHash(PaymentGatewayConfig config, Map<String, String> params);

    /** Process refund via bank API */
    PosRefundResult refund(PaymentGatewayConfig config, PosRefundRequest request);

    /** Test connection to bank endpoint — used by admin "Test Connection" button */
    boolean testConnection(PaymentGatewayConfig config);
}
