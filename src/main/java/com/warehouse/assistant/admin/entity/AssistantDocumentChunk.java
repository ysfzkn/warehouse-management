package com.warehouse.assistant.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A chunked slice of an uploaded document, ready for vector retrieval.
 * <p>
 * IMPORTANT: the {@code embedding} column (pgvector {@code vector(1536)}) is
 * intentionally <b>not</b> mapped here. Hibernate/JPA has no native pgvector
 * type; managing it via the entity would force a custom UserType and risks
 * {@code ddl-auto=validate} breaking on deploy. All vector inserts and
 * similarity reads happen via native SQL in {@code VectorSearchService}
 * using {@code JdbcTemplate}. The JPA entity exists only for metadata CRUD.
 */
@Entity
@Table(name = "assistant_document_chunk")
@Data
@NoArgsConstructor
public class AssistantDocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
