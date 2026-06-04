package com.warehouse.assistant.store.tools;

import com.warehouse.assistant.store.dto.StoreProductCard;
import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compare two products side-by-side. Returns enriched cards so the LLM can
 * build a Turkish markdown table itself (name, marka, kategori, fiyat, stok).
 */
@Component
public class StoreProductCompareTool {

    private final ProductService productService;
    private final StockService stockService;

    public StoreProductCompareTool(ProductService productService, StockService stockService) {
        this.productService = productService;
        this.stockService = stockService;
    }

    @Tool(description = "STORE: İki ürünü karşılaştır. Kullanıcı iki ürünü kıyaslamak istediğinde kullan. "
            + "Her iki ürünün id'si gerekiyor (önce searchProducts ile id'leri bul).")
    public List<StoreProductCard> compareProducts(
            @ToolParam(description = "Birinci ürün id") Long productAId,
            @ToolParam(description = "İkinci ürün id") Long productBId) {
        List<StoreProductCard> out = new ArrayList<>();
        addIfPresent(out, productAId);
        addIfPresent(out, productBId);
        return out;
    }

    private void addIfPresent(List<StoreProductCard> out, Long id) {
        if (id == null) return;
        Optional<Product> maybe = productService.getProductByIdWithRelations(id);
        if (maybe.isEmpty()) return;
        Product p = maybe.get();
        if (!p.isActive()) return;
        long stock = stockService.getTotalQuantityByProduct(p.getId());
        out.add(StoreProductSearchTool.toCard(p, stock));
    }
}
