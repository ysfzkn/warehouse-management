package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class CheckoutValidationResponse {
    private boolean valid;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal total;
    private List<String> errors;
}
