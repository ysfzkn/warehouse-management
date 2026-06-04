package com.warehouse.assistant.core.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link ProductIndexEvent}s after the owning transaction commits,
 * so we never index a product that was rolled back. Runs on the Spring async
 * executor to keep the request path snappy — if the embedding call is slow
 * (Azure round-trip), the product save still returns immediately.
 */
@Component
public class ProductEmbeddingListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingListener.class);

    private final ProductEmbeddingIndexer indexer;

    public ProductEmbeddingListener(ProductEmbeddingIndexer indexer) {
        this.indexer = indexer;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductIndexEvent event) {
        try {
            switch (event.kind()) {
                case UPSERT -> indexer.reindex(event.productId());
                case DELETE -> indexer.remove(event.productId());
            }
        } catch (Exception e) {
            // Swallow — embedding is a best-effort sidecar. The dashboard's
            // "Re-index products" button can retry later.
            log.warn("Product embedding event handling failed for id={}: {}",
                    event.productId(), e.getMessage());
        }
    }
}
