package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class PlaceOrderResponse {
    private Long orderId;
    private String orderNumber;
    private BigDecimal grandTotal;
    private String status;
    private String paymentUrl;

    /**
     * One-time proof of ownership for {@code POST /api/store/payment/initialize}.
     * Returned only here, to the browser that placed the order; never readable from
     * any other endpoint.
     */
    private String paymentToken;
}
