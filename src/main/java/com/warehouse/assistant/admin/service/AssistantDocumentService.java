package com.warehouse.assistant.admin.service;

import com.warehouse.assistant.admin.entity.AssistantDocument;
import com.warehouse.assistant.admin.entity.AssistantDocumentChunk;
import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.admin.entity.AssistantDocumentStatus;
import com.warehouse.assistant.admin.repository.AssistantDocumentChunkRepository;
import com.warehouse.assistant.admin.repository.AssistantDocumentRepository;
import com.warehouse.assistant.core.rag.DocumentChunker;
import com.warehouse.assistant.core.rag.EmbeddingService;
import com.warehouse.assistant.core.rag.VectorSearchService;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * End-to-end document ingestion pipeline for the admin FAQ / policy uploads.
 * <ol>
 *   <li>Persist the file to disk and create a PENDING row.</li>
 *   <li>Return the row id to the controller immediately (async pipeline).</li>
 *   <li>Extract text with Tika, chunk it, embed via Azure, persist chunks.</li>
 *   <li>Flip the row to READY — or FAILED with an error_message on any throw.</li>
 * </ol>
 * Chunk metadata rows are created via JPA; the embedding column itself is
 * written via {@link VectorSearchService#writeChunkEmbedding(long, float[])}
 * because Hibernate can't handle pgvector natively.
 */
@Service
public class AssistantDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AssistantDocumentService.class);
    private static final String STORAGE_PREFIX = "assistant-documents";

    private final AssistantDocumentRepository documentRepository;
    private final AssistantDocumentChunkRepository chunkRepository;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final com.warehouse.service.PhotoStorageService photoStorageService;
    private final Tika tika = new Tika();

    public AssistantDocumentService(AssistantDocumentRepository documentRepository,
                                    AssistantDocumentChunkRepository chunkRepository,
                                    DocumentChunker chunker,
                                    EmbeddingService embeddingService,
                                    VectorSearchService vectorSearchService,
                                    com.warehouse.service.PhotoStorageService photoStorageService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.photoStorageService = photoStorageService;
    }

    /**
     * @return true when both the embedding model and pgvector schema are
     *         available. When false, uploads still accept the file but the
     *         row ends up in status FAILED with a friendly error message.
     */
    public boolean isIngestionAvailable() {
        return embeddingService.isAvailable() && vectorSearchService.isRagAvailable();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public AssistantDocument uploadAndQueue(MultipartFile file,
                                            String title,
                                            AssistantDocumentScope scope,
                                            String uploadedBy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Yüklenecek dosya boş olamaz.");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        // Storage abstraction üzerinden kaydet → dev'de local fs, prod'da S3/MinIO.
        String storageKey;
        try (InputStream in = file.getInputStream()) {
            storageKey = photoStorageService.storeDocument(
                    STORAGE_PREFIX,
                    fileName,
                    file.getContentType(),
                    in
            );
        }

        AssistantDocument doc = new AssistantDocument();
        doc.setTitle(title != null && !title.isBlank() ? title.trim() : fileName);
        doc.setScope(scope != null ? scope : AssistantDocumentScope.STORE);
        doc.setFileName(fileName);
        doc.setStoragePath(storageKey);
        doc.setMimeType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setUploadedBy(uploadedBy);
        doc.setStatus(AssistantDocumentStatus.PENDING);
        AssistantDocument saved = documentRepository.save(doc);

        // Kick off async processing.
        processDocumentAsync(saved.getId());
        return saved;
    }

    /** Admin-triggered re-index. Same async flow, runs against the existing file on disk. */
    @Transactional
    public void reindex(Long documentId) {
        if (documentId == null) return;
        documentRepository.findById(documentId).ifPresent(doc -> {
            chunkRepository.deleteByDocumentId(documentId);
            doc.setStatus(AssistantDocumentStatus.PENDING);
            doc.setChunkCount(0);
            doc.setErrorMessage(null);
            documentRepository.save(doc);
            processDocumentAsync(documentId);
        });
    }

    @Transactional
    public void delete(Long documentId) {
        if (documentId == null) return;
        documentRepository.findById(documentId).ifPresent(doc -> {
            chunkRepository.deleteByDocumentId(documentId);
            try {
                photoStorageService.deleteDocument(doc.getStoragePath());
            } catch (Exception e) {
                log.warn("Could not delete document {}: {}", doc.getStoragePath(), e.getMessage());
            }
            documentRepository.delete(doc);
        });
    }

    // -------------------------------------------------------------------------
    // Async pipeline
    // -------------------------------------------------------------------------

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDocumentAsync(Long documentId) {
        AssistantDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("[DocIngest] document not found, id={}", documentId);
            return;
        }

        long tStart = System.nanoTime();
        log.info("[DocIngest] ▶ START id={} title=\"{}\" scope={} file=\"{}\"",
                documentId, doc.getTitle(), doc.getScope(), doc.getStoragePath());

        try {
            doc.setStatus(AssistantDocumentStatus.INDEXING);
            documentRepository.save(doc);

            if (!embeddingService.isAvailable()) {
                throw new IllegalStateException("Embedding servisi kullanılabilir değil — Azure OpenAI embedding deployment ayarını kontrol edin.");
            }
            if (!vectorSearchService.isRagAvailable()) {
                throw new IllegalStateException("Vector store kullanılamıyor — pgvector eklentisi yüklü değil. "
                        + "Docker: pgvector/pgvector:pg15 imajı kullanın veya Postgres sunucusuna 'CREATE EXTENSION vector' yetkisi verin.");
            }

            long t1 = System.nanoTime();
            String text;
            try (InputStream in = photoStorageService.openDocumentStream(doc.getStoragePath())) {
                text = tika.parseToString(in);
            }
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Dosyadan metin çıkarılamadı.");
            }
            log.info("[DocIngest] ① text extracted — {} chars ({}ms)",
                    text.length(), (System.nanoTime() - t1) / 1_000_000);

            long t2 = System.nanoTime();
            List<String> chunks = chunker.chunk(text);
            log.info("[DocIngest] ② chunked — {} chunks (avg {} chars, {}ms)",
                    chunks.size(),
                    chunks.isEmpty() ? 0 : chunks.stream().mapToInt(String::length).sum() / chunks.size(),
                    (System.nanoTime() - t2) / 1_000_000);

            long t3 = System.nanoTime();
            int indexed = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                AssistantDocumentChunk chunkRow = new AssistantDocumentChunk();
                chunkRow.setDocumentId(documentId);
                chunkRow.setChunkIndex(i);
                chunkRow.setContent(chunkText);
                long cT = System.nanoTime();
                final int idx = i;
                embeddingService.embed(chunkText).ifPresent(vec -> {
                    chunkRepository.save(chunkRow);
                    vectorSearchService.writeChunkEmbedding(chunkRow.getId(), vec);
                    log.debug("[DocIngest]   chunk#{}/{} id={} dims={} ({}ms) preview=\"{}\"",
                            idx + 1, chunks.size(), chunkRow.getId(), vec.length,
                            (System.nanoTime() - cT) / 1_000_000,
                            preview(chunkText, 80));
                });
                if (chunkRow.getId() != null) indexed++;
                else log.warn("[DocIngest]   chunk#{}/{} skipped — embedding returned empty", idx + 1, chunks.size());
            }
            log.info("[DocIngest] ③ embedded — {}/{} chunks indexed ({}ms)",
                    indexed, chunks.size(), (System.nanoTime() - t3) / 1_000_000);

            doc.setChunkCount(indexed);
            doc.setStatus(AssistantDocumentStatus.READY);
            doc.setIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);
            log.info("[DocIngest] ✓ DONE id={} — {} chunks, total {}ms",
                    documentId, indexed, (System.nanoTime() - tStart) / 1_000_000);
        } catch (Exception e) {
            log.error("[DocIngest] ✗ FAILED id={} — {}", documentId, e.getMessage(), e);
            doc.setStatus(AssistantDocumentStatus.FAILED);
            doc.setErrorMessage(truncate(e.getMessage(), 1024));
            documentRepository.save(doc);
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // persistToDisk / extractText artık kullanılmıyor — tüm I/O
    // PhotoStorageService.storeDocument + openDocumentStream üzerinden.

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
