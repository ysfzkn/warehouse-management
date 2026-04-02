package com.warehouse.service.payment;

import com.warehouse.dto.payment.*;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.service.PaymentConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class BankTransferGateway implements PaymentGateway {

    private final PaymentConfigService paymentConfigService;

    public BankTransferGateway(PaymentConfigService paymentConfigService) {
        this.paymentConfigService = paymentConfigService;
    }

    @Override
    public PaymentProvider getProvider() { return PaymentProvider.BANK_TRANSFER; }

    @Override
    public PaymentInitResult initializePayment(PaymentInitRequest request) {
        // Read config dynamically from DB (no restart needed)
        Map<String, String> cfg = paymentConfigService.getBankTransferConfig();

        String reference = "HVL-" + request.getOrderNumber() + "-" + String.format("%04d", new Random().nextInt(10000));

        Map<String, String> bankDetails = new HashMap<>();
        bankDetails.put("bankName", cfg.getOrDefault("bankName", ""));
        bankDetails.put("iban", cfg.getOrDefault("iban", ""));
        bankDetails.put("accountHolder", cfg.getOrDefault("accountHolder", ""));
        bankDetails.put("reference", reference);
        bankDetails.put("amount", request.getPaidPrice().toPlainString() + " " + request.getCurrency());

        return PaymentInitResult.builder()
            .success(true)
            .bankTransferReference(reference)
            .bankDetails(bankDetails)
            .rawResponse(Map.of("reference", reference))
            .build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        throw new UnsupportedOperationException("Havale/EFT icin callback desteklenmez. Admin panelden onay yapilir.");
    }

    @Override
    public PaymentStatusResult queryStatus(String token) {
        return PaymentStatusResult.builder().status(com.warehouse.enums.PaymentStatus.INITIATED).build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return RefundResult.builder()
            .success(true)
            .refundedAmount(request.getAmount())
            .rawResponse(Map.of("note", "Havale iadesi manuel olarak bankacilik sistemi uzerinden yapilmalidir."))
            .build();
    }

    @Override
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        return InstallmentQueryResult.builder().options(List.of()).build();
    }
}
