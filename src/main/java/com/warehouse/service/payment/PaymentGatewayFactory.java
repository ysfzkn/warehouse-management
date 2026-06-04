package com.warehouse.service.payment;

import com.warehouse.config.PaymentProperties;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentProvider, PaymentGateway> gateways;
    private final PaymentProperties paymentProperties;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList, PaymentProperties paymentProperties) {
        this.gateways = new HashMap<>();
        for (PaymentGateway gw : gatewayList) {
            gateways.put(gw.getProvider(), gw);
            // VirtualPosGateway handles NESTPAY, GVP, and PAYTR protocols
            if (gw instanceof VirtualPosGateway) {
                gateways.put(PaymentProvider.GVP, gw);
                gateways.put(PaymentProvider.NESTPAY, gw);
                gateways.put(PaymentProvider.PAYTR, gw);
            }
        }
        this.paymentProperties = paymentProperties;
    }

    public PaymentGateway getGateway(PaymentProvider provider) {
        PaymentGateway gw = gateways.get(provider);
        if (gw == null) {
            throw new WarehouseManagementException(ErrorCode.PAYMENT_INIT_FAILED,
                "Ödeme sağlayıcısı desteklenmiyor: " + provider);
        }
        return gw;
    }

    public PaymentGateway getDefaultGateway() {
        return getGateway(PaymentProvider.valueOf(paymentProperties.getProvider()));
    }
}
