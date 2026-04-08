package com.warehouse.assistant.admin.entity;

/**
 * Lifecycle of a document in the ingestion pipeline.
 * <pre>
 *   PENDING   → row created, file on disk, not yet chunked
 *   INDEXING  → chunker + embedding in progress
 *   READY     → all chunks persisted with embeddings, searchable
 *   FAILED    → terminal; see error_message column
 * </pre>
 */
public enum AssistantDocumentStatus {
    PENDING,
    INDEXING,
    READY,
    FAILED
}
