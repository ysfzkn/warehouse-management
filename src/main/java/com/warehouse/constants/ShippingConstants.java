package com.warehouse.constants;

import java.math.BigDecimal;

/**
 * Last-resort defaults, used only when the matching site setting is missing or unreadable.
 *
 * <p>The rule that turns these into a price lives in {@code ShippingPriceService}. It used to
 * live here too, as a method that ignored every setting and every carrier — and the checkout
 * validation endpoint called it, so that screen quoted 29,99 whatever the shop had configured.
 */
public final class ShippingConstants {

    private ShippingConstants() {}

    public static final BigDecimal DEFAULT_SHIPPING_COST = new BigDecimal("29.99");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500");
}
