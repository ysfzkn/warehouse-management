package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.core.config.AssistantProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Low-level vector operations against pgvector columns.
 * <p>
 * We deliberately avoid JPA here: Hibernate has no native pgvector type and
 * trying to bolt one on runs afoul of {@code ddl-auto=validate}. Native SQL
 * via {@link JdbcTemplate} gives us full control over the vector literal
 * format ({@code '[1.0, 2.0, ...]'::vector}) and the cosine distance
 * operator ({@code <=>}).
 * <p>
 * All reads return {@link VectorSearchResult} lists trimmed to
 * {@code assistant.rag.vector-top-k} and filtered by
 * {@code assistant.rag.vector-distance-threshold}.
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddingService;
    private final AssistantProperties props;

    /**
     * Cached at boot: true when the pgvector extension AND the vector columns
     * are actually present in this database. When false, all vector methods
     * short-circuit to empty results without touching the DB. This lets the
     * app boot on a vanilla Postgres (Railway default, local dev without
     * pgvector) with only RAG features disabled — everything else keeps
     * working.
     */
    private volatile boolean ragAvailable;

    public VectorSearchService(JdbcTemplate jdbc,
                               EmbeddingService embeddingService,
                               AssistantProperties props) {
        this.jdbc = jdbc;
        this.embeddingService = embeddingService;
        this.props = props;
    }

    @PostConstruct
    public void detectPgVector() {
        try {
            Integer extCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
            boolean hasExtension = extCount != null && extCount > 0;

            Integer tableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_name = 'product_embedding'",
                    Integer.class);
            boolean hasProductTable = tableCount != null && tableCount > 0;

            Integer colCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() "
                            + "AND table_name = 'assistant_document_chunk' AND column_name = 'embedding'",
                    Integer.class);
            boolean hasEmbeddingColumn = colCount != null && colCount > 0;

            this.ragAvailable = hasExtension && hasProductTable && hasEmbeddingColumn;
            if (ragAvailable) {
                log.info("Assistant RAG: ENABLED (pgvector extension + vector columns detected).");
            } else {
                log.warn("Assistant RAG: DISABLED (pgvector={}, product_embedding_table={}, embedding_col={}). "
                        + "Install pgvector and re-run migrations to enable product semantic search + FAQ retrieval. "
                        + "Docker image: pgvector/pgvector:pg15.",
                        hasExtension, hasProductTable, hasEmbeddingColumn);
            }
        } catch (Exception e) {
            this.ragAvailable = false;
            log.warn("Assistant RAG: DISABLED (pgvector detection failed: {}). Continuing without vector search.",
                    e.getMessage());
        }
    }

    /** @return true when the database has pgvector and all expected vector schema. */
    public boolean isRagAvailable() {
        return ragAvailable;
    }

    // -------------------------------------------------------------------------
    // Product embeddings
    // -------------------------------------------------------------------------

    /**
     * Upsert a product embedding. Skips the write if {@code contentHash} is
     * unchanged (save Azure tokens on product updates that don't affect text).
     */
    @Transactional
    public boolean upsertProductEmbedding(long productId, String contentHash, float[] vector) {
        if (!ragAvailable) return false;
        if (vector == null || vector.length == 0) return false;
        String literal = toPgVectorLiteral(vector);

        // Short-circuit if hash unchanged.
        Integer existingCount = jdbc.queryForObject(
                "select count(*) from product_embedding where product_id = ? and content_hash = ?",
                Integer.class, productId, contentHash);
        if (existingCount != null && existingCount > 0) {
            return false;
        }

        jdbc.update("""
                insert into product_embedding (product_id, content_hash, embedding, updated_at)
                values (?, ?, ?::vector, now())
                on conflict (product_id) do update
                   set content_hash = excluded.content_hash,
                       embedding    = excluded.embedding,
                       updated_at   = now()
                """, productId, contentHash, literal);
        return true;
    }

    @Transactional
    public void deleteProductEmbedding(long productId) {
        if (!ragAvailable) return;
        jdbc.update("delete from product_embedding where product_id = ?", productId);
    }

    /**
     * Semantic search over the product catalog. Returns hits whose cosine
     * distance is below the configured threshold, up to top-k.
     */
    public List<VectorSearchResult> searchProducts(String query) {
        return embeddingService.embed(query)
                .map(vec -> searchProductsByVector(vec))
                .orElse(List.of());
    }

    public List<VectorSearchResult> searchProductsByVector(float[] vector) {
        if (!ragAvailable) return List.of();
        if (vector == null || vector.length == 0) return List.of();
        String literal = toPgVectorLiteral(vector);
        int topK = Math.max(1, props.getRag().getVectorTopK());
        double threshold = props.getRag().getVectorDistanceThreshold();

        try {
            return jdbc.query("""
                    select pe.product_id,
                           p.name,
                           (pe.embedding <=> ?::vector) as distance
                      from product_embedding pe
                      join products p on p.id = pe.product_id
                     where p.is_active = true
                     order by pe.embedding <=> ?::vector
                     limit ?
                    """,
                    (rs, i) -> new VectorSearchResult(
                            rs.getLong("product_id"),
                            rs.getString("name"),
                            rs.getDouble("distance")),
                    literal, literal, topK)
                    .stream()
                    .filter(r -> r.distance() <= threshold)
                    .toList();
        } catch (Exception e) {
            log.error("Product vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Document chunk embeddings (FAQ / policies)
    // -------------------------------------------------------------------------

    /**
     * Insert a single document chunk row including its embedding.
     * The metadata row in {@code assistant_document_chunk} is written by the
     * caller via JPA; this call only sets the {@code embedding} column on an
     * existing row identified by {@code chunkId}.
     */
    @Transactional
    public void writeChunkEmbedding(long chunkId, float[] vector) {
        if (!ragAvailable) return;
        if (vector == null || vector.length == 0) return;
        String literal = toPgVectorLiteral(vector);
        jdbc.update("update assistant_document_chunk set embedding = ?::vector where id = ?",
                literal, chunkId);
    }

    /**
     * Semantic search over indexed FAQ/policy chunks, filtered by scope.
     * Pass {@link AssistantDocumentScope#STORE} for storefront queries,
     * {@link AssistantDocumentScope#WMS} for admin assistant queries — in both
     * cases chunks with scope {@link AssistantDocumentScope#BOTH} are included.
     */
    public List<VectorSearchResult> searchDocumentChunks(String query, AssistantDocumentScope scope) {
        return embeddingService.embed(query)
                .map(vec -> searchDocumentChunksByVector(vec, scope))
                .orElse(List.of());
    }

    public List<VectorSearchResult> searchDocumentChunksByVector(float[] vector, AssistantDocumentScope scope) {
        if (!ragAvailable) return List.of();
        if (vector == null || vector.length == 0 || scope == null) return List.of();
        String literal = toPgVectorLiteral(vector);
        int topK = Math.max(1, props.getRag().getVectorTopK());
        double threshold = props.getRag().getVectorDistanceThreshold();

        List<String> allowed = new ArrayList<>();
        allowed.add("BOTH");
        if (scope == AssistantDocumentScope.WMS || scope == AssistantDocumentScope.BOTH) allowed.add("WMS");
        if (scope == AssistantDocumentScope.STORE || scope == AssistantDocumentScope.BOTH) allowed.add("STORE");
        String inClause = String.join(",", allowed.stream().map(s -> "'" + s + "'").toList());

        try {
            String sql = String.format(Locale.ROOT, """
                    select c.id,
                           c.content,
                           (c.embedding <=> ?::vector) as distance
                      from assistant_document_chunk c
                      join assistant_document d on d.id = c.document_id
                     where d.status = 'READY'
                       and d.scope in (%s)
                     order by c.embedding <=> ?::vector
                     limit ?
                    """, inClause);
            return jdbc.query(sql,
                    (rs, i) -> new VectorSearchResult(
                            rs.getLong("id"),
                            rs.getString("content"),
                            rs.getDouble("distance")),
                    literal, literal, topK)
                    .stream()
                    .filter(r -> r.distance() <= threshold)
                    .toList();
        } catch (Exception e) {
            log.error("Document chunk vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Format a float array as a pgvector text literal: {@code [1.0,2.0,3.0]}. */
    public static String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
