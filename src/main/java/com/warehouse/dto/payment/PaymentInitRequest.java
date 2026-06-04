package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data @Builder
public class PaymentInitRequest {
    private String orderId;
    private String orderNumber;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private String currency;
    private int installmentCount;
    private String callbackUrl;

    // Buyer info
    private String buyerId;
    private String buyerName;
    private String buyerSurname;
    private String buyerEmail;
    private String buyerPhone;
    private String buyerIdentityNumber;
    private String buyerIp;
    private String buyerCity;
    private String buyerCountry;
    private String buyerAddress;

    // Shipping address
    private String shippingContactName;
    private String shippingCity;
    private String shippingCountry;
    private String shippingAddress;

    // Billing address
    private String billingContactName;
    private String billingCity;
    private String billingCountry;
    private String billingAddress;

    // Basket items
    private List<BasketItemDto> basketItems;

    @Data @Builder
    public static class BasketItemDto {
        private String id;
        private String name;
        private String category;
        private BigDecimal price;
    }
}
