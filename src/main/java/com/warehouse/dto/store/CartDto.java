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
}
