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
     * KDV oranı bazlı kırılım (Türkiye e-ticaret standardı).
     * Fiyatlar KDV dahildir; bu breakdown sepet özetinde "KDV (%18): X TL"
     * şeklinde göstermek ve fatura sırasında ayrıştırmak için kullanılır.
     * Anahtar: oran (örn. "20.00"), değer: o oran üzerinden hesaplanan KDV tutarı.
     */
    @Builder.Default
    private java.util.Map<String, BigDecimal> vatBreakdown = java.util.Collections.emptyMap();
}
