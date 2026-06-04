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
}
