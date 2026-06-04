package com.warehouse.service.payment;

import com.warehouse.dto.payment.*;
import com.warehouse.enums.PaymentProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Door payment (cash/card on delivery) — auto-completes immediately.
 * No external gateway call needed.
 */
@Component
public class DoorPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider getProvider() { return PaymentProvider.DOOR_PAYMENT; }

    @Override
    public PaymentInitResult initializePayment(PaymentInitRequest request) {
        return PaymentInitResult.builder()
                .success(true)
                .token("DOOR-" + request.getOrderNumber())
                .rawResponse(Map.of("method", "DOOR_PAYMENT", "orderNumber", request.getOrderNumber()))
                .build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        return PaymentCallbackResult.builder().success(true).build();
    }

    @Override
    public PaymentStatusResult queryStatus(String token) {
        return PaymentStatusResult.builder().status(com.warehouse.enums.PaymentStatus.SUCCESS).build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return RefundResult.builder()
                .success(true)
                .refundedAmount(request.getAmount())
                .rawResponse(Map.of("note", "Kapıda ödeme iadesi manuel olarak yapılmalıdır."))
                .build();
    }

    @Override
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        return InstallmentQueryResult.builder().options(List.of()).build();
    }
}
