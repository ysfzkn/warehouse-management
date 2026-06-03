package com.warehouse.assistant.store.tools;

import com.warehouse.assistant.core.rag.VectorSearchResult;
import com.warehouse.assistant.core.rag.VectorSearchService;
import com.warehouse.assistant.store.dto.StoreProductCard;
import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Semantic fallback search via pgvector. Call this only after
 * {@link StoreProductSearchTool} returns an empty list — or when the user's
 * query is descriptive rather than attribute-based ("something that matches my
 * home furnishings", "quiet", "a gift for my family").
 */
@Component
public class StoreVectorSearchTool {

    private final VectorSearchService vectorSearchService;
    private final ProductService productService;
    private final StockService stockService;

    public StoreVectorSearchTool(VectorSearchService vectorSearchService,
                                 ProductService productService,
                                 StockService stockService) {
        this.vectorSearchService = vectorSearchService;
        this.productService = productService;
        this.stockService = stockService;
    }

    @Tool(description = "STORE: Anlamsal (semantik) ürün araması. Yapılandırılmış arama sonuç vermediğinde veya "
            + "kullanıcı soyut/tanımsal sorduğunda kullan ('sessiz buzdolabı', 'küçük daireye uygun'). "
            + "Doğal dildeki sorgudan en yakın ürünleri pgvector ile döndürür.")
    public List<StoreProductCard> semanticSearchProducts(
            @ToolParam(description = "Kullanıcının doğal dildeki ürün açıklaması") String query) {
        if (query == null || query.isBlank()) return List.of();

        List<VectorSearchResult> hits = vectorSearchService.searchProducts(query.trim());
        if (hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream().map(VectorSearchResult::id).toList();
        Map<Long, Long> stockTotals = stockService.getTotalQuantitiesByProductIds(ids);

        return ids.stream()
                .map(productService::getProductByIdWithRelations)
                .flatMap(java.util.Optional::stream)
                .filter(Product::isActive)
                .map(p -> StoreProductSearchTool.toCard(p, stockTotals.getOrDefault(p.getId(), 0L)))
                .toList();
    }
}
