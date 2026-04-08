package com.warehouse.assistant.store.dto;

import java.math.BigDecimal;

/**
 * Lightweight product representation that store tools return to the LLM.
 * The frontend {@code ProductCards} renderer displays these as clickable
 * cards with image, price, and "Sepete Ekle" / "Detay" buttons.
 */
public class StoreProductCard {
    public Long id;
    public String name;
    public String sku;
    public String slug;
    public String brand;
    public String category;
    public BigDecimal price;
    public BigDecimal discountedPrice;
    public Long totalStock;
    public String shortDescription;
    public String imageUrl;
    public boolean inStock;
}
