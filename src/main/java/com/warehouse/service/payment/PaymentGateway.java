package com.warehouse.service.payment;

import com.warehouse.dto.payment.*;
import com.warehouse.enums.PaymentProvider;
import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {
    PaymentProvider getProvider();
    PaymentInitResult initializePayment(PaymentInitRequest request);
    PaymentCallbackResult handleCallback(Map<String, String> params);
    PaymentStatusResult queryStatus(String token);
    RefundResult refund(RefundRequest request);
    InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price);
}
