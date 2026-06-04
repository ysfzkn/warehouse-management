package com.warehouse.service;

import com.warehouse.config.PaymentProperties;
import com.warehouse.dto.payment.PaymentInitRequest;
import com.warehouse.dto.payment.PaymentInitResult;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.service.impl.PaymentConfigServiceImpl;
import com.warehouse.service.payment.BankTransferGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransferGatewayTest {

    private BankTransferGateway gateway;

    @BeforeEach
    void setUp() {
        PaymentConfigService configService = mock(PaymentConfigService.class);
        when(configService.getBankTransferConfig()).thenReturn(Map.of(
            "bankName", "Test Bank",
            "iban", "TR1234567890",
            "accountHolder", "Test Ltd",
            "deadlineHours", "48",
            "configured", "true"
        ));
        gateway = new BankTransferGateway(configService);
    }

    @Test
    void should_return_bank_transfer_provider() {
        assertThat(gateway.getProvider()).isEqualTo(PaymentProvider.BANK_TRANSFER);
    }

    @Test
    void should_generate_reference_code() {
        PaymentInitRequest request = PaymentInitRequest.builder()
            .orderNumber("ORD-TEST-001").paidPrice(new BigDecimal("100")).currency("TRY").build();

        PaymentInitResult result = gateway.initializePayment(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBankTransferReference()).startsWith("HVL-ORD-TEST-001-");
        assertThat(result.getBankDetails()).containsKey("iban");
        assertThat(result.getBankDetails().get("bankName")).isEqualTo("Test Bank");
    }

    @Test
    void should_throw_unsupported_for_callback() {
        assertThatThrownBy(() -> gateway.handleCallback(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_empty_installment_options() {
        var result = gateway.getInstallmentOptions("123456", new BigDecimal("100"));
        assertThat(result.getOptions()).isEmpty();
    }
}
