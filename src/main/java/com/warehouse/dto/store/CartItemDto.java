package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class CartItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSlug;
    private String productSku;
    private String imageUrl;
    private BigDecimal unitPrice;
    private BigDecimal salePrice;
    private BigDecimal lineTotal;
    /** VAT rate (in Turkey prices are VAT-inclusive; this rate is for the invoice and the summary breakdown). */
    private BigDecimal vatRate;
    private int quantity;
    private int availableStock;
    private String stockStatus;
}
