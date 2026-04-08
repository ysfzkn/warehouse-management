package com.warehouse.assistant.store.tools;

import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stock availability lookup for a specific product. Returns a lightweight
 * summary the LLM can turn into Turkish prose ("Evet, stokta 12 adet var" /
 * "Şu an stokta bulunmuyor").
 */
@Component
public class StoreStockCheckTool {

    private final ProductService productService;
    private final StockService stockService;

    public StoreStockCheckTool(ProductService productService, StockService stockService) {
        this.productService = productService;
        this.stockService = stockService;
    }

    public static class StockInfo {
        public Long productId;
        public String productName;
        public long totalQuantity;
        public boolean inStock;
        public String estimatedDeliveryHint;
    }

    @Tool(description = "STORE: Bir ürünün stok ve teslimat durumunu kontrol et. productId veya sku'dan en az biri gerekli.")
    public StockInfo checkStock(
            @ToolParam(description = "Ürün id (opsiyonel)", required = false) Long productId,
            @ToolParam(description = "Stok kodu/SKU (opsiyonel)", required = false) String sku) {
        Optional<Product> maybe = resolveProduct(productId, sku);
        if (maybe.isEmpty()) return null;

        Product p = maybe.get();
        long total = stockService.getTotalQuantityByProduct(p.getId());

        StockInfo info = new StockInfo();
        info.productId = p.getId();
        info.productName = p.getName();
        info.totalQuantity = total;
        info.inStock = total > 0;
        info.estimatedDeliveryHint = total > 0
                ? "1-3 iş günü içinde kargoya verilir"
                : "Şu anda stokta bulunmuyor";
        return info;
    }

    private Optional<Product> resolveProduct(Long productId, String sku) {
        if (productId != null) {
            return productService.getProductByIdWithRelations(productId);
        }
        if (sku != null && !sku.isBlank()) {
            return productService.getProductBySku(sku.trim());
        }
        return Optional.empty();
    }
}
