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
    private static final String STORAGE_ROOT = "uploads/assistant-documents";

    private final AssistantDocumentRepository documentRepository;
    private final AssistantDocumentChunkRepository chunkRepository;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final Tika tika = new Tika();

    public AssistantDocumentService(AssistantDocumentRepository documentRepository,
                                    AssistantDocumentChunkRepository chunkRepository,
                                    DocumentChunker chunker,
                                    EmbeddingService embeddingService,
                                    VectorSearchService vectorSearchService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
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
        Path storedPath = persistToDisk(file, fileName);

        AssistantDocument doc = new AssistantDocument();
        doc.setTitle(title != null && !title.isBlank() ? title.trim() : fileName);
        doc.setScope(scope != null ? scope : AssistantDocumentScope.STORE);
        doc.setFileName(fileName);
        doc.setStoragePath(storedPath.toString());
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
                Files.deleteIfExists(Paths.get(doc.getStoragePath()));
            } catch (IOException e) {
                log.warn("Could not delete file {}: {}", doc.getStoragePath(), e.getMessage());
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
        if (doc == null) return;

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

            String text = extractText(Paths.get(doc.getStoragePath()));
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Dosyadan metin çıkarılamadı.");
            }

            List<String> chunks = chunker.chunk(text);
            int indexed = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                AssistantDocumentChunk chunkRow = new AssistantDocumentChunk();
                chunkRow.setDocumentId(documentId);
                chunkRow.setChunkIndex(i);
                chunkRow.setContent(chunkText);
                // A placeholder row won't satisfy the NOT NULL embedding column, so we
                // need the embedding FIRST, then write the row + vector together via
                // a native insert.
                embeddingService.embed(chunkText).ifPresent(vec -> {
                    chunkRepository.save(chunkRow);
                    vectorSearchService.writeChunkEmbedding(chunkRow.getId(), vec);
                });
                if (chunkRow.getId() != null) indexed++;
            }

            doc.setChunkCount(indexed);
            doc.setStatus(AssistantDocumentStatus.READY);
            doc.setIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);
            log.info("Document {} indexed: {} chunks", documentId, indexed);
        } catch (Exception e) {
            log.error("Document ingestion failed for id={}: {}", documentId, e.getMessage(), e);
            doc.setStatus(AssistantDocumentStatus.FAILED);
            doc.setErrorMessage(truncate(e.getMessage(), 1024));
            documentRepository.save(doc);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path persistToDisk(MultipartFile file, String fileName) throws IOException {
        Path dir = Paths.get(STORAGE_ROOT);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String ext = extensionOf(fileName);
        String safeName = UUID.randomUUID() + ext;
        Path target = dir.resolve(safeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot).toLowerCase() : "";
    }

    private String extractText(Path file) throws IOException, TikaException {
        try (InputStream in = Files.newInputStream(file)) {
            return tika.parseToString(in);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
