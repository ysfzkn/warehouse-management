package com.warehouse.assistant.core.rag;

import com.warehouse.entity.Product;
import com.warehouse.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Turns a {@link Product} row into the text we embed, computes a content hash
 * to skip no-op re-indexing, and hands the result off to
 * {@link VectorSearchService}.
 * <p>
 * The embedding template is intentionally concise: we want the embedding to
 * capture <i>what the product is</i>, not marketing copy. Dense fields first
 * (name, brand, category), then short description, bounded at ~1000 chars to
 * keep token usage predictable.
 */
@Service
public class ProductEmbeddingIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingIndexer.class);
    private static final int MAX_DESC_CHARS = 600;

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;

    public ProductEmbeddingIndexer(ProductRepository productRepository,
                                   EmbeddingService embeddingService,
                                   VectorSearchService vectorSearchService) {
        this.productRepository = productRepository;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
    }

    /** Embed and upsert a single product by id. Safe to call on a missing id. */
    @Transactional
    public void reindex(long productId) {
        if (!embeddingService.isAvailable() || !vectorSearchService.isRagAvailable()) return;

        Optional<Product> maybe = productRepository.findById(productId);
        if (maybe.isEmpty()) {
            log.debug("Skipping embedding for missing product id={}", productId);
            return;
        }
        Product p = maybe.get();
        String text = buildContentText(p);
        if (text.isBlank()) return;

        String hash = sha256(text);
        embeddingService.embed(text)
                .ifPresent(vec -> {
                    boolean wrote = vectorSearchService.upsertProductEmbedding(productId, hash, vec);
                    if (wrote) {
                        log.debug("Embedded product id={} ({} chars)", productId, text.length());
                    }
                });
    }

    /** Remove a product from the vector index. */
    @Transactional
    public void remove(long productId) {
        vectorSearchService.deleteProductEmbedding(productId);
    }

    /**
     * Build the text representation that gets embedded. Tries to include the
     * most search-worthy signals without pulling in noisy HTML descriptions.
     */
    public String buildContentText(Product p) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        appendIfNotBlank(sb, p.getName());
        if (p.getBrand() != null) appendIfNotBlank(sb, "Marka: " + p.getBrand().getName());
        if (p.getCategory() != null) appendIfNotBlank(sb, "Kategori: " + p.getCategory().getName());
        if (p.getColor() != null) appendIfNotBlank(sb, "Renk: " + p.getColor().getName());
        appendIfNotBlank(sb, "Stok kodu: " + p.getSku());
        String desc = p.getDescription();
        if (desc != null && !desc.isBlank()) {
            String clean = desc.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (clean.length() > MAX_DESC_CHARS) {
                clean = clean.substring(0, MAX_DESC_CHARS);
            }
            appendIfNotBlank(sb, clean);
        }
        return sb.toString().trim();
    }

    private static void appendIfNotBlank(StringBuilder sb, String s) {
        if (s != null && !s.isBlank()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(s.trim());
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
