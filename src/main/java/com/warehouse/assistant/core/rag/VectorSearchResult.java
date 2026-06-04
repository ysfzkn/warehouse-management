package com.warehouse.assistant.core.rag;

/**
 * A single hit returned from a vector similarity search.
 *
 * @param id       row id of the hit (product id, chunk id, etc.)
 * @param content  raw textual content of the hit
 * @param distance cosine distance in [0, 2]; lower means more similar
 */
public record VectorSearchResult(long id, String content, double distance) {
}
