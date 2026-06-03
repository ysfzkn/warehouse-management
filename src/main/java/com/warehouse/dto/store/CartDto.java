package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class CartDto {
    private Long id;
    private List<CartItemDto> items;
    private int itemCount;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private String couponCode;
    private String couponDescription;

    /**
     * VAT-rate-based breakdown (Turkish e-commerce standard).
     * Prices are VAT-inclusive; this breakdown is used to display lines such as
     * "VAT (18%): X TL" in the cart summary and to itemize them on the invoice.
     * Key: rate (e.g. "20.00"), value: the VAT amount computed at that rate.
     */
    @Builder.Default
    private java.util.Map<String, BigDecimal> vatBreakdown = java.util.Collections.emptyMap();
}
