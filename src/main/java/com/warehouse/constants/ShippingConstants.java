package com.warehouse.constants;

import java.math.BigDecimal;

public final class ShippingConstants {

    private ShippingConstants() {}

    public static final BigDecimal DEFAULT_SHIPPING_COST = new BigDecimal("29.99");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500");

    public static BigDecimal calculateShippingCost(BigDecimal subtotal) {
        if (subtotal != null && subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return DEFAULT_SHIPPING_COST;
    }
}
