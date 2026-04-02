package com.warehouse.dto.store;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceOrderRequest {
    @NotNull(message = "Teslimat adresi zorunludur")
    private Long shippingAddressId;
    private Long billingAddressId;
    private String cargoCompany;
    private String paymentMethod;
    private String customerNote;
    private boolean distanceSalesContractAccepted;
}
