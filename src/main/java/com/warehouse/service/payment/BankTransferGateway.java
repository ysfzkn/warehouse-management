package com.warehouse.service.payment;

import com.warehouse.dto.payment.*;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.service.PaymentConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BankTransferGateway implements PaymentGateway {

    private final PaymentConfigService paymentConfigService;
    /** SecureRandom — unpredictable compared to Random; low collision probability. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public BankTransferGateway(PaymentConfigService paymentConfigService) {
        this.paymentConfigService = paymentConfigService;
    }

    @Override
    public PaymentProvider getProvider() { return PaymentProvider.BANK_TRANSFER; }

    @Override
    public PaymentInitResult initializePayment(PaymentInitRequest request) {
        // Read config dynamically from DB (no restart needed)
        Map<String, String> cfg = paymentConfigService.getBankTransferConfig();

        // Reference = HVL{orderNumber}{6-digit-secure-random}
        // No dashes — some banks strip dashes from the description field.
        String reference = "HVL" + request.getOrderNumber()
                + String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        Map<String, String> bankDetails = new HashMap<>();
        bankDetails.put("bankName", cfg.getOrDefault("bankName", ""));
        bankDetails.put("iban", cfg.getOrDefault("iban", ""));
        bankDetails.put("accountHolder", cfg.getOrDefault("accountHolder", ""));
        bankDetails.put("reference", reference);
        bankDetails.put("amount", request.getPaidPrice().toPlainString() + " " + request.getCurrency());

        // FAST QR — when enabled and an image is uploaded from the admin panel
        if ("true".equalsIgnoreCase(cfg.getOrDefault("qrEnabled", "false"))) {
            bankDetails.put("qrEnabled", "true");
            bankDetails.put("qrImage", cfg.getOrDefault("qrImage", ""));
            bankDetails.put("qrBankName", cfg.getOrDefault("qrBankName", ""));
            bankDetails.put("qrDescription", cfg.getOrDefault("qrDescription", ""));
        }

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
        // CRITICAL: bank transfer refunds cannot be automated — they require a manual
        // transfer from the bank account. It used to return success=true, so the admin
        // would think it was "refunded" while the customer received no money. We now
        // return an explicit failure: the admin must perform the manual refund and change
        // the order status by hand (or via a future "manual refund recorded" endpoint).
        return RefundResult.builder()
            .success(false)
            .errorMessage("Havale iadesi otomatik yapılamaz. "
                    + "Müşteri IBAN'ına banka panelinizden manuel iade yapın, "
                    + "sonra admin panelinden 'Manuel İade Kaydet' işlemini uygulayın "
                    + "(banka işlem referansını da ekleyin).")
            .rawResponse(Map.of(
                    "providerType", "BANK_TRANSFER",
                    "automaticRefundSupported", "false",
                    "manualRefundRequired", "true"))
            .build();
    }

    @Override
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        return InstallmentQueryResult.builder().options(List.of()).build();
    }
}
