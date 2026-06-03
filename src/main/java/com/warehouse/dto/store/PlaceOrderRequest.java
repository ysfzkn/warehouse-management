package com.warehouse.dto.store;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlaceOrderRequest {
    @NotNull(message = "Teslimat adresi zorunludur")
    private Long shippingAddressId;
    private Long billingAddressId;
    private String cargoCompany;
    private Long cargoProviderId;
    private String paymentMethod;
    private String customerNote;

    // --- Legal contracts (required by Turkish Law No. 6502) ---
    private boolean distanceSalesContractAccepted;
    private LocalDateTime distanceSalesContractAcceptedAt;
    private boolean preliminaryInfoAccepted;
    private LocalDateTime preliminaryInfoAcceptedAt;
}
