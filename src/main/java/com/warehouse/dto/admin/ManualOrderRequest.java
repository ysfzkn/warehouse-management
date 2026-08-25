package com.warehouse.dto.admin;

import com.warehouse.enums.DeliveryMethod;
import com.warehouse.enums.ManualPaymentState;
import com.warehouse.enums.OrderChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ManualOrderRequest {
    private Long customerId;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank private String phone;
    private String email;
    @NotNull private OrderChannel channel;
    private String channelReference;
    @NotBlank private String paymentMethod;
    @NotNull private ManualPaymentState paymentState;
    private LocalDateTime paymentDueAt;
    private LocalDateTime reminderAt;
    /** CARGO → cargoProviderId is required; OWN_TRANSFER → shipment is tracked as a stock transfer. */
    private DeliveryMethod deliveryMethod = DeliveryMethod.CARGO;
    private Long cargoProviderId;
    /** Optional — a tracking number that already exists at the time the order is written down. */
    private String cargoTrackingNo;
    private BigDecimal shippingCost = BigDecimal.ZERO;
    private String note;
    @NotNull private Map<String, Object> shippingAddress;
    private Map<String, Object> billingAddress;
    @Valid @NotEmpty private List<Item> items;

    @Data
    public static class Item {
        @NotNull private Long productId;
        @NotNull @Min(1) private Integer quantity;
        private BigDecimal unitPrice;
    }
}
