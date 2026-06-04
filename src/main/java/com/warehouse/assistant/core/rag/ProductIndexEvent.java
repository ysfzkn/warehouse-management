package com.warehouse.assistant.core.rag;

/**
 * Published whenever a product is created, updated, or deleted. The
 * {@link ProductEmbeddingListener} reacts to this to keep the pgvector index
 * in sync. Using a domain event decouples {@code ProductService} from the AI
 * subsystem — when the assistant is disabled, the listener simply does
 * nothing.
 */
public record ProductIndexEvent(long productId, Kind kind) {
    public enum Kind { UPSERT, DELETE }

    public static ProductIndexEvent upsert(long productId) {
        return new ProductIndexEvent(productId, Kind.UPSERT);
    }

    public static ProductIndexEvent delete(long productId) {
        return new ProductIndexEvent(productId, Kind.DELETE);
    }
}
