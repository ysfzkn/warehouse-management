package com.warehouse.assistant.store.tools;

import com.warehouse.assistant.core.rag.VectorSearchResult;
import com.warehouse.assistant.core.rag.VectorSearchService;
import com.warehouse.assistant.store.dto.StoreProductCard;
import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Structured product search — the assistant's primary tool for "find me a
 * product that matches this description" queries. Uses the existing
 * {@link ProductService#getAllProducts(Pageable, String, Long, Long, Long)}
 * filtered query (active products only) and wraps results in cards the
 * frontend can render directly.
 */
@Component
public class StoreProductSearchTool {

    private static final Logger log = LoggerFactory.getLogger(StoreProductSearchTool.class);

    private final ProductService productService;
    private final StockService stockService;
    private final VectorSearchService vectorSearchService;

    public StoreProductSearchTool(ProductService productService,
                                  StockService stockService,
                                  VectorSearchService vectorSearchService) {
        this.productService = productService;
        this.stockService = stockService;
        this.vectorSearchService = vectorSearchService;
    }

    @Tool(description = "STORE: Ürün arama. Kullanıcı bir ürün, kategori veya marka ismi (ör. 'televizyon', "
            + "'buzdolabı', 'Samsung', 'LG çamaşır makinesi') söylediğinde bu tool'u çağır — search parametresine "
            + "kullanıcının söylediği kelimeyi olduğu gibi ver. Tool kelimeyi ürün adı, SKU, kategori adı VE marka "
            + "adında arar; o yüzden 'televizyon' → Televizyon kategorisindeki ürünler, 'Samsung' → Samsung markalı "
            + "ürünler dönecektir. Yapılandırılmış arama boş dönerse tool otomatik olarak semantik aramaya düşer — "
            + "yine de boşsa gerçekten stokta yok demektir. Parametreler: "
            + "search (serbest metin), categoryId, brandId (opsiyonel; id biliniyorsa kullan), "
            + "minPrice, maxPrice (TL), page (0'dan başlar), size (1-20, default 8). "
            + "Sadece aktif ürünler döner. Sonuç listesini 'Aşağıda size uygun seçenekler:' öncülüyle sun.")
    public List<StoreProductCard> searchProducts(
            @ToolParam(description = "Ürün adı veya anahtar kelimeler (Türkçe)", required = false) String search,
            @ToolParam(description = "Kategori id'si (opsiyonel)", required = false) Long categoryId,
            @ToolParam(description = "Marka id'si (opsiyonel — listBrands ile çöz)", required = false) Long brandId,
            @ToolParam(description = "Minimum fiyat TL (opsiyonel)", required = false) BigDecimal minPrice,
            @ToolParam(description = "Maksimum fiyat TL (opsiyonel)", required = false) BigDecimal maxPrice,
            @ToolParam(description = "Sayfa numarası (0'dan başlar)", required = false) Integer page,
            @ToolParam(description = "Sayfa boyutu (1-20)", required = false) Integer size) {

        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 20) : 8;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

        String normalized = (search != null && !search.isBlank()) ? search.trim() : null;

        try {
            Page<Product> products = productService.getAllProducts(pageable, normalized, categoryId, brandId, null);
            List<Product> filtered = products.getContent().stream()
                    .filter(Product::isActive)
                    .filter(p -> minPrice == null || p.getPrice() == null || p.getPrice().compareTo(minPrice) >= 0)
                    .filter(p -> maxPrice == null || p.getPrice() == null || p.getPrice().compareTo(maxPrice) <= 0)
                    .toList();

            if (!filtered.isEmpty()) {
                List<Long> ids = filtered.stream().map(Product::getId).toList();
                Map<Long, Long> stockTotals = stockService.getTotalQuantitiesByProductIds(ids);
                return filtered.stream().map(p -> toCard(p, stockTotals.getOrDefault(p.getId(), 0L))).toList();
            }

            // Structured search returned nothing — auto-fallback to semantic (pgvector) search
            // so the LLM doesn't need a second tool call for the common "not in my exact phrasing"
            // case. Only triggers when the user actually provided search text; id-only queries
            // are never overridden.
            if (normalized != null && categoryId == null && brandId == null) {
                log.debug("Structured search empty for '{}' → falling back to semantic.", normalized);
                return semanticFallback(normalized, safeSize, minPrice, maxPrice);
            }
            return List.of();
        } catch (Exception e) {
            log.warn("StoreProductSearchTool failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** pgvector semantic fallback when name/SKU/category/brand text search misses. */
    private List<StoreProductCard> semanticFallback(String query, int size, BigDecimal minPrice, BigDecimal maxPrice) {
        try {
            List<VectorSearchResult> hits = vectorSearchService.searchProducts(query);
            if (hits.isEmpty()) return List.of();
            List<Long> ids = hits.stream().map(VectorSearchResult::id).toList();
            Map<Long, Long> stockTotals = stockService.getTotalQuantitiesByProductIds(ids);
            return ids.stream()
                    .map(productService::getProductByIdWithRelations)
                    .flatMap(java.util.Optional::stream)
                    .filter(Product::isActive)
                    .filter(p -> minPrice == null || p.getPrice() == null || p.getPrice().compareTo(minPrice) >= 0)
                    .filter(p -> maxPrice == null || p.getPrice() == null || p.getPrice().compareTo(maxPrice) <= 0)
                    .limit(size)
                    .map(p -> toCard(p, stockTotals.getOrDefault(p.getId(), 0L)))
                    .toList();
        } catch (Exception e) {
            log.debug("Semantic fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    static StoreProductCard toCard(Product p, long totalStock) {
        StoreProductCard card = new StoreProductCard();
        card.id = p.getId();
        card.name = p.getName();
        card.sku = p.getSku();
        card.slug = p.getSlug();
        card.brand = p.getBrand() != null ? p.getBrand().getName() : null;
        card.category = p.getCategory() != null ? p.getCategory().getName() : null;
        card.price = p.getPrice();
        card.totalStock = totalStock;
        card.inStock = totalStock > 0;
        String desc = p.getDescription();
        if (desc != null) {
            String cleaned = desc.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            card.shortDescription = cleaned.length() > 180 ? cleaned.substring(0, 180) + "..." : cleaned;
        }
        return card;
    }
}
