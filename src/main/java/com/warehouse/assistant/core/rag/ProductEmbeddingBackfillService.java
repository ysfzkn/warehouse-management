package com.warehouse.assistant.core.rag;

import com.warehouse.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * One-shot / admin-triggered backfill: walks all active products and ensures
 * each one has an up-to-date embedding. Idempotent — the indexer's
 * content-hash short-circuit means unchanged products cost zero Azure tokens.
 */
@Service
public class ProductEmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingBackfillService.class);
    private static final int BATCH_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductEmbeddingIndexer indexer;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;

    public ProductEmbeddingBackfillService(ProductRepository productRepository,
                                           ProductEmbeddingIndexer indexer,
                                           EmbeddingService embeddingService,
                                           VectorSearchService vectorSearchService) {
        this.productRepository = productRepository;
        this.indexer = indexer;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
    }

    /** @return number of products processed. */
    public int backfillAll() {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding service unavailable — product backfill skipped.");
            return 0;
        }
        if (!vectorSearchService.isRagAvailable()) {
            log.warn("pgvector not available — product backfill skipped. Install the pgvector extension and re-run migrations.");
            return 0;
        }
        int processed = 0;
        int page = 0;
        while (true) {
            Page<com.warehouse.entity.Product> slice =
                    productRepository.findAll(PageRequest.of(page, BATCH_SIZE));
            if (slice.isEmpty()) break;
            for (com.warehouse.entity.Product p : slice.getContent()) {
                try {
                    indexer.reindex(p.getId());
                    processed++;
                } catch (Exception e) {
                    log.warn("Backfill failed for product id={}: {}", p.getId(), e.getMessage());
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("Product embedding backfill complete. processed={}", processed);
        return processed;
    }
}
