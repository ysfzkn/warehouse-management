package com.warehouse.service.payment;

import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.BasketItemType;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.CheckoutForm;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.model.InstallmentDetail;
import com.iyzipay.model.InstallmentInfo;
import com.iyzipay.model.PaymentGroup;
import com.iyzipay.model.Refund;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.iyzipay.request.CreateRefundRequest;
import com.iyzipay.request.RetrieveCheckoutFormRequest;
import com.iyzipay.request.RetrieveInstallmentInfoRequest;
import com.warehouse.config.PaymentProperties;
import com.warehouse.dto.payment.*;
import com.warehouse.enums.PaymentProvider;
import com.warehouse.service.PaymentConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class IyzicoPaymentGateway implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(IyzicoPaymentGateway.class);
    private final PaymentConfigService paymentConfigService;
    private final PaymentProperties.IyzicoConfig fallbackConfig;

    public IyzicoPaymentGateway(PaymentConfigService paymentConfigService, PaymentProperties paymentProperties) {
        this.paymentConfigService = paymentConfigService;
        this.fallbackConfig = paymentProperties.getIyzico();
    }

    /** Build Options from DB config on each call — allows runtime changes without restart */
    private Options buildOptions() {
        Map<String, String> cfg = paymentConfigService.getIyzicoConfigRaw();
        Options opts = new Options();
        opts.setApiKey(cfg.getOrDefault("apiKey", fallbackConfig.getApiKey()));
        opts.setSecretKey(cfg.getOrDefault("secretKey", fallbackConfig.getSecretKey()));
        opts.setBaseUrl(cfg.getOrDefault("baseUrl", fallbackConfig.getBaseUrl()));
        return opts;
    }

    private String getCallbackUrl() {
        Map<String, String> cfg = paymentConfigService.getIyzicoConfigRaw();
        String url = cfg.get("callbackUrl");
        return (url != null && !url.isEmpty()) ? url : fallbackConfig.getCallbackUrl();
    }

    @Override
    public PaymentProvider getProvider() { return PaymentProvider.IYZICO; }

    @Override
    public PaymentInitResult initializePayment(PaymentInitRequest request) {
        try {
            CreateCheckoutFormInitializeRequest iyzicoRequest = new CreateCheckoutFormInitializeRequest();
            iyzicoRequest.setLocale(com.iyzipay.model.Locale.TR.getValue());
            iyzicoRequest.setConversationId(request.getOrderNumber());
            iyzicoRequest.setPrice(request.getPrice());
            iyzicoRequest.setPaidPrice(request.getPaidPrice());
            iyzicoRequest.setCurrency(com.iyzipay.model.Currency.TRY.name());
            iyzicoRequest.setBasketId(request.getOrderNumber());
            iyzicoRequest.setPaymentGroup(PaymentGroup.PRODUCT.name());
            iyzicoRequest.setCallbackUrl(request.getCallbackUrl() != null ? request.getCallbackUrl() : getCallbackUrl());
            iyzicoRequest.setEnabledInstallments(Arrays.asList(1, 2, 3, 6, 9, 12));
            iyzicoRequest.setForceThreeDS(1);

            // Buyer
            Buyer buyer = new Buyer();
            buyer.setId(request.getBuyerId());
            buyer.setName(request.getBuyerName());
            buyer.setSurname(request.getBuyerSurname());
            buyer.setEmail(request.getBuyerEmail());
            buyer.setGsmNumber(request.getBuyerPhone());
            buyer.setIdentityNumber(request.getBuyerIdentityNumber() != null ? request.getBuyerIdentityNumber() : "11111111111");
            buyer.setRegistrationAddress(request.getBuyerAddress());
            buyer.setCity(request.getBuyerCity());
            buyer.setCountry(request.getBuyerCountry() != null ? request.getBuyerCountry() : "Turkey");
            buyer.setIp(request.getBuyerIp());
            iyzicoRequest.setBuyer(buyer);

            // Shipping Address
            Address shippingAddr = new Address();
            shippingAddr.setContactName(request.getShippingContactName());
            shippingAddr.setCity(request.getShippingCity());
            shippingAddr.setCountry(request.getShippingCountry() != null ? request.getShippingCountry() : "Turkey");
            shippingAddr.setAddress(request.getShippingAddress());
            iyzicoRequest.setShippingAddress(shippingAddr);

            // Billing Address
            Address billingAddr = new Address();
            billingAddr.setContactName(request.getBillingContactName());
            billingAddr.setCity(request.getBillingCity());
            billingAddr.setCountry(request.getBillingCountry() != null ? request.getBillingCountry() : "Turkey");
            billingAddr.setAddress(request.getBillingAddress());
            iyzicoRequest.setBillingAddress(billingAddr);

            // Basket items
            List<BasketItem> basketItems = new ArrayList<>();
            for (PaymentInitRequest.BasketItemDto item : request.getBasketItems()) {
                BasketItem bi = new BasketItem();
                bi.setId(item.getId());
                bi.setName(item.getName());
                bi.setCategory1(item.getCategory());
                bi.setItemType(BasketItemType.PHYSICAL.name());
                bi.setPrice(item.getPrice());
                basketItems.add(bi);
            }
            iyzicoRequest.setBasketItems(basketItems);

            CheckoutFormInitialize result = CheckoutFormInitialize.create(iyzicoRequest, buildOptions());

            if (result.getStatus() != null && result.getStatus().equals("success")) {
                return PaymentInitResult.builder()
                    .success(true)
                    .htmlContent(result.getCheckoutFormContent())
                    .token(result.getToken())
                    .rawResponse(Map.of("status", result.getStatus(), "token", result.getToken()))
                    .build();
            } else {
                logger.error("iyzico init failed: {} - {}", result.getErrorCode(), result.getErrorMessage());
                return PaymentInitResult.builder()
                    .success(false)
                    .errorCode(result.getErrorCode())
                    .errorMessage(result.getErrorMessage())
                    .rawResponse(Map.of("errorCode", String.valueOf(result.getErrorCode()), "errorMessage", String.valueOf(result.getErrorMessage())))
                    .build();
            }
        } catch (Exception e) {
            logger.error("iyzico init exception: {}", e.getMessage(), e);
            return PaymentInitResult.builder()
                .success(false)
                .errorMessage("Odeme sistemi hatasi: " + e.getMessage())
                .build();
        }
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        try {
            String token = params.get("token");
            if (token == null || token.isBlank()) {
                return PaymentCallbackResult.builder().success(false).errorMessage("Token eksik").build();
            }

            RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setToken(token);

            CheckoutForm result = CheckoutForm.retrieve(request, buildOptions());

            boolean success = "success".equals(result.getPaymentStatus());

            return PaymentCallbackResult.builder()
                .success(success)
                .token(token)
                .paymentId(result.getPaymentId() != null ? result.getPaymentId() : "")
                .paidPrice(result.getPaidPrice())
                .installmentCount(result.getInstallment() != null ? result.getInstallment() : 1)
                .cardLastFour(result.getBinNumber() != null ? result.getBinNumber().substring(Math.max(0, result.getBinNumber().length() - 4)) : null)
                .cardType(result.getCardType())
                .cardAssociation(result.getCardAssociation())
                .cardFamily(result.getCardFamily())
                .threeDSecure("1".equals(String.valueOf(result.getFraudStatus())))
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .rawResponse(Map.of(
                    "status", String.valueOf(result.getStatus()),
                    "paymentStatus", String.valueOf(result.getPaymentStatus()),
                    "paymentId", String.valueOf(result.getPaymentId())
                ))
                .build();
        } catch (Exception e) {
            logger.error("iyzico callback error: {}", e.getMessage(), e);
            return PaymentCallbackResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public PaymentStatusResult queryStatus(String token) {
        try {
            RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setToken(token);
            CheckoutForm result = CheckoutForm.retrieve(request, buildOptions());
            boolean success = "success".equals(result.getPaymentStatus());
            return PaymentStatusResult.builder()
                .status(success ? com.warehouse.enums.PaymentStatus.SUCCESS : com.warehouse.enums.PaymentStatus.PROCESSING)
                .paidAmount(result.getPaidPrice())
                .build();
        } catch (Exception e) {
            return PaymentStatusResult.builder().status(com.warehouse.enums.PaymentStatus.PROCESSING).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            CreateRefundRequest iyzicoRequest = new CreateRefundRequest();
            iyzicoRequest.setLocale(com.iyzipay.model.Locale.TR.getValue());
            iyzicoRequest.setConversationId(request.getConversationId());
            iyzicoRequest.setPaymentTransactionId(request.getPaymentTransactionId());
            iyzicoRequest.setPrice(request.getAmount());
            iyzicoRequest.setCurrency(com.iyzipay.model.Currency.TRY.name());
            iyzicoRequest.setIp(request.getIp());
            iyzicoRequest.setReason(com.iyzipay.model.RefundReason.OTHER);

            Refund result = Refund.create(iyzicoRequest, buildOptions());
            boolean success = "success".equals(result.getStatus());

            return RefundResult.builder()
                .success(success)
                .refundedAmount(request.getAmount())
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .rawResponse(Map.of("status", String.valueOf(result.getStatus())))
                .build();
        } catch (Exception e) {
            logger.error("iyzico refund error: {}", e.getMessage(), e);
            return RefundResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        try {
            RetrieveInstallmentInfoRequest request = new RetrieveInstallmentInfoRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setBinNumber(binNumber);
            request.setPrice(price);

            InstallmentInfo result = InstallmentInfo.retrieve(request, buildOptions());

            if (result.getInstallmentDetails() != null && !result.getInstallmentDetails().isEmpty()) {
                var detail = result.getInstallmentDetails().get(0);
                List<InstallmentQueryResult.InstallmentOption> opts = detail.getInstallmentPrices().stream()
                    .map(ip -> InstallmentQueryResult.InstallmentOption.builder()
                        .installmentCount(ip.getInstallmentNumber() != null ? ip.getInstallmentNumber() : 1)
                        .installmentPrice(ip.getInstallmentPrice())
                        .totalPrice(ip.getTotalPrice())
                        .build())
                    .collect(Collectors.toList());

                return InstallmentQueryResult.builder()
                    .binNumber(binNumber)
                    .cardType(detail.getCardType())
                    .cardAssociation(detail.getCardAssociation())
                    .cardFamily(detail.getCardFamilyName())
                    .bankName(detail.getBankName())
                    .options(opts)
                    .build();
            }
            return InstallmentQueryResult.builder().binNumber(binNumber).options(List.of()).build();
        } catch (Exception e) {
            logger.error("iyzico installment query error: {}", e.getMessage(), e);
            return InstallmentQueryResult.builder().binNumber(binNumber).options(List.of()).build();
        }
    }
}
