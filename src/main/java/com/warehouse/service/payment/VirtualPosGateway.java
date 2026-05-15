package com.warehouse.service.payment;

import com.warehouse.dto.payment.*;
import com.warehouse.entity.PaymentGatewayConfig;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.repository.PaymentGatewayConfigRepository;
import com.warehouse.service.payment.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Unified gateway for direct bank virtual POS integrations.
 * Delegates to protocol-specific handlers (NestPay, GVP) based on the gateway config.
 * Implements PaymentGateway so it plugs into the existing strategy pattern seamlessly.
 */
@Component
public class VirtualPosGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(VirtualPosGateway.class);

    private final PaymentGatewayConfigRepository configRepo;
    private final BankPosProtocolFactory protocolFactory;

    public VirtualPosGateway(PaymentGatewayConfigRepository configRepo, BankPosProtocolFactory protocolFactory) {
        this.configRepo = configRepo;
        this.protocolFactory = protocolFactory;
    }

    @Override
    public PaymentProvider getProvider() {
        // Returns NESTPAY as default — the factory registers this for both NESTPAY and GVP
        return PaymentProvider.NESTPAY;
    }

    @Override
    public PaymentInitResult initializePayment(PaymentInitRequest request) {
        PaymentGatewayConfig config = resolveConfig();
        if (config == null) {
            return PaymentInitResult.builder()
                    .success(false)
                    .errorMessage("Aktif sanal POS yapılandırması bulunamadı.")
                    .build();
        }

        BankPosProtocol protocol = protocolFactory.getProtocol(config.getGatewayProtocol());

        String callbackUrl = config.getCallbackUrl() != null ? config.getCallbackUrl()
                : request.getCallbackUrl();
        if (callbackUrl == null) callbackUrl = "";

        // Customer ad-soyad birleştir — PayTR user_name tek alan
        String fullName = ((request.getBuyerName() != null ? request.getBuyerName() : "")
                + (request.getBuyerSurname() != null ? " " + request.getBuyerSurname() : "")).trim();

        PosPaymentRequest posRequest = PosPaymentRequest.builder()
                .orderId(String.valueOf(request.getOrderId()))
                .orderNumber(request.getOrderNumber())
                .amount(request.getPaidPrice())
                .currency(request.getCurrency() != null ? request.getCurrency() : "TRY")
                .installmentCount(request.getInstallmentCount())
                .customerIp(request.getBuyerIp())
                .customerEmail(request.getBuyerEmail())
                .customerName(fullName)
                .customerPhone(request.getBuyerPhone())
                .customerAddress(request.getBuyerAddress())
                .okUrl(callbackUrl)
                .failUrl(callbackUrl)
                .build();

        PosPaymentInitResult posResult = protocol.initialize3DPayment(config, posRequest);

        return PaymentInitResult.builder()
                .success(posResult.isSuccess())
                .htmlContent(posResult.getHtmlContent())
                .token(posResult.getTransactionId())
                .errorMessage(posResult.getErrorMessage())
                .errorCode(posResult.getErrorCode())
                .rawResponse(posResult.getRawResponse())
                .build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        // Callback routing is done by configCode in the controller
        // This method is called with the config already resolved
        String configCode = params.get("_configCode");
        PaymentGatewayConfig config = configCode != null
                ? configRepo.findByCode(configCode).orElse(null)
                : resolveConfig();

        if (config == null) {
            return PaymentCallbackResult.builder()
                    .success(false)
                    .errorMessage("Gateway yapılandırması bulunamadı.")
                    .build();
        }

        BankPosProtocol protocol = protocolFactory.getProtocol(config.getGatewayProtocol());

        // CRITICAL: Verify hash FIRST before any processing
        if (!protocol.verifyCallbackHash(config, params)) {
            log.error("SECURITY: Hash verification FAILED for config={}, possible fraud!", config.getCode());
            return PaymentCallbackResult.builder()
                    .success(false)
                    .errorMessage("Güvenlik doğrulaması başarısız. İşlem reddedildi.")
                    .build();
        }

        PosCallbackResult posResult = protocol.handle3DCallback(config, params);

        return PaymentCallbackResult.builder()
                .success(posResult.isSuccess())
                .token(posResult.getTransactionId())
                .paymentId(posResult.getTransactionId())
                .cardLastFour(posResult.getCardLastFour())
                .cardType(posResult.getCardType())
                .cardAssociation(posResult.getCardAssociation())
                .threeDSecure(posResult.isThreeDSecure())
                .errorCode(posResult.getErrorCode())
                .errorMessage(posResult.getErrorMessage())
                .rawResponse(posResult.getRawResponse())
                .build();
    }

    @Override
    public PaymentStatusResult queryStatus(String token) {
        // Bank POS typically doesn't have a query API in the same way
        return PaymentStatusResult.builder()
                .status(com.warehouse.enums.PaymentStatus.PROCESSING)
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        PaymentGatewayConfig config = resolveConfig();
        if (config == null) {
            return RefundResult.builder().success(false).errorMessage("Aktif POS bulunamadı.").build();
        }
        BankPosProtocol protocol = protocolFactory.getProtocol(config.getGatewayProtocol());
        PosRefundResult posResult = protocol.refund(config, PosRefundRequest.builder()
                .transactionId(request.getPaymentTransactionId())
                .orderId(request.getConversationId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .ip(request.getIp())
                .build());
        return RefundResult.builder()
                .success(posResult.isSuccess())
                .refundedAmount(posResult.getRefundedAmount())
                .errorCode(posResult.getErrorCode())
                .errorMessage(posResult.getErrorMessage())
                .rawResponse(posResult.getRawResponse())
                .build();
    }

    @Override
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        // Direct bank POS installment options are typically configured per terminal, not queried
        return InstallmentQueryResult.builder().options(List.of()).build();
    }

    /** Resolves the active default gateway configuration */
    public PaymentGatewayConfig resolveConfig() {
        return configRepo.findFirstByActiveTrueAndDefaultGatewayTrueOrderByPriorityAsc().orElse(null);
    }

    /** Resolves a specific gateway configuration by code */
    public PaymentGatewayConfig resolveConfigByCode(String code) {
        return configRepo.findByCode(code).orElse(null);
    }
}
