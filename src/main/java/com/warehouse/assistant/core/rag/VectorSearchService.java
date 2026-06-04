package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.core.config.AssistantProperties;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final AssistantRuntimeConfig runtimeConfig;

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
                               AssistantProperties props,
                               AssistantRuntimeConfig runtimeConfig) {
        this.jdbc = jdbc;
        this.embeddingService = embeddingService;
        this.props = props;
        this.runtimeConfig = runtimeConfig;
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
        log.info("[RAG] searchProducts ← query=\"{}\"", previewQuery(query));
        return embeddingService.embed(query)
                .map(vec -> {
                    log.debug("[RAG] searchProducts embedded query → dims={}", vec.length);
                    return searchProductsByVector(vec);
                })
                .orElseGet(() -> {
                    log.warn("[RAG] searchProducts → empty embedding (service unavailable or blank query)");
                    return List.of();
                });
    }

    public List<VectorSearchResult> searchProductsByVector(float[] vector) {
        if (!ragAvailable) {
            log.debug("[RAG] searchProductsByVector: ragAvailable=false, returning empty");
            return List.of();
        }
        if (vector == null || vector.length == 0) return List.of();
        String literal = toPgVectorLiteral(vector);
        int topK = Math.max(1, runtimeConfig.getVectorTopK());
        double threshold = runtimeConfig.getVectorDistanceThreshold();

        long t0 = System.nanoTime();
        try {
            List<VectorSearchResult> raw = jdbc.query("""
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
                    literal, literal, topK);
            List<VectorSearchResult> filtered = raw.stream()
                    .filter(r -> r.distance() <= threshold)
                    .toList();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("[RAG] searchProductsByVector → {}/{} hits (threshold={}, topK={}, {}ms)",
                    filtered.size(), raw.size(), threshold, topK, ms);
            for (int i = 0; i < Math.min(filtered.size(), 10); i++) {
                VectorSearchResult r = filtered.get(i);
                log.debug("[RAG]   hit#{} productId={} dist={} name=\"{}\"",
                        i + 1, r.id(), String.format(Locale.ROOT, "%.4f", r.distance()),
                        truncate(r.content(), 80));
            }
            return filtered;
        } catch (Exception e) {
            log.error("[RAG] Product vector search failed: {}", e.getMessage(), e);
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
        log.info("[RAG] searchDocumentChunks ← scope={} query=\"{}\" hybrid={}",
                scope, previewQuery(query), runtimeConfig.isHybridEnabled());

        List<VectorSearchResult> hits;
        if (runtimeConfig.isHybridEnabled()) {
            hits = hybridSearchDocumentChunks(query, scope);
        } else {
            hits = embeddingService.embed(query)
                    .map(vec -> searchDocumentChunksByVector(vec, scope))
                    .orElseGet(() -> {
                        log.warn("[RAG] searchDocumentChunks → empty embedding");
                        return List.of();
                    });
        }

        int window = Math.max(0, runtimeConfig.getNeighborWindow());
        if (window > 0 && !hits.isEmpty()) {
            return expandWithNeighbors(hits, window);
        }
        return hits;
    }

    /**
     * Hybrid retrieval: combines pgvector cosine similarity (semantic) with
     * PostgreSQL full-text {@code ts_rank} scoring (BM25-ish / keyword) using
     * Reciprocal Rank Fusion (RRF):
     *
     * <pre>score(doc) = Σ over rankers r:  1 / (k + rank_r(doc))</pre>
     *
     * Standard RRF is parameter-robust and biases toward documents that
     * appear near the top of <b>both</b> lists — exactly what "KVKK MADDE 6"
     * style queries need where vector alone might miss the literal term.
     */
    private List<VectorSearchResult> hybridSearchDocumentChunks(String query, AssistantDocumentScope scope) {
        if (!ragAvailable) return List.of();
        if (query == null || query.isBlank() || scope == null) return List.of();

        int topK = Math.max(1, runtimeConfig.getVectorTopK());
        int candidatePool = topK * 4;  // pull more from each side for better fusion
        int k = Math.max(1, runtimeConfig.getHybridRrfK());

        // 1) Vector candidates
        var vecOpt = embeddingService.embed(query);
        List<VectorSearchResult> vectorHits = vecOpt
                .map(vec -> rawVectorTopKChunks(vec, scope, candidatePool))
                .orElse(List.of());

        // 2) BM25-ish candidates via ts_rank
        List<VectorSearchResult> keywordHits = keywordTopKChunks(query, scope, candidatePool);

        log.info("[RAG] hybrid — vector={} keyword={} (fusing with RRF k={})",
                vectorHits.size(), keywordHits.size(), k);

        // 3) RRF fusion
        Map<Long, Double> scores = new java.util.HashMap<>();
        Map<Long, VectorSearchResult> lookup = new java.util.HashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            VectorSearchResult r = vectorHits.get(i);
            scores.merge(r.id(), 1.0 / (k + i + 1), Double::sum);
            lookup.putIfAbsent(r.id(), r);
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            VectorSearchResult r = keywordHits.get(i);
            scores.merge(r.id(), 1.0 / (k + i + 1), Double::sum);
            lookup.putIfAbsent(r.id(), r);
        }

        // 4) Sort by fused score, return top-K; preserve original distance for UI display
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> lookup.get(e.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Vector top-K without the distance threshold filter (for hybrid candidate pool). */
    private List<VectorSearchResult> rawVectorTopKChunks(float[] vec, AssistantDocumentScope scope, int topK) {
        if (vec == null || vec.length == 0) return List.of();
        String literal = toPgVectorLiteral(vec);
        List<String> allowed = new ArrayList<>();
        allowed.add("BOTH");
        if (scope == AssistantDocumentScope.WMS || scope == AssistantDocumentScope.BOTH) allowed.add("WMS");
        if (scope == AssistantDocumentScope.STORE || scope == AssistantDocumentScope.BOTH) allowed.add("STORE");
        String inClause = String.join(",", allowed.stream().map(s -> "'" + s + "'").toList());

        try {
            String sql = String.format(Locale.ROOT, """
                    select c.id, c.content, (c.embedding <=> ?::vector) as distance
                      from assistant_document_chunk c
                      join assistant_document d on d.id = c.document_id
                     where d.status = 'READY'
                       and c.embedding is not null
                       and d.scope in (%s)
                     order by c.embedding <=> ?::vector
                     limit ?
                    """, inClause);
            return jdbc.query(sql,
                    (rs, i) -> new VectorSearchResult(
                            rs.getLong("id"), rs.getString("content"), rs.getDouble("distance")),
                    literal, literal, topK);
        } catch (Exception e) {
            log.error("[RAG] rawVectorTopKChunks failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Keyword search via PostgreSQL ts_rank + plainto_tsquery. */
    private List<VectorSearchResult> keywordTopKChunks(String query, AssistantDocumentScope scope, int topK) {
        List<String> allowed = new ArrayList<>();
        allowed.add("BOTH");
        if (scope == AssistantDocumentScope.WMS || scope == AssistantDocumentScope.BOTH) allowed.add("WMS");
        if (scope == AssistantDocumentScope.STORE || scope == AssistantDocumentScope.BOTH) allowed.add("STORE");
        String inClause = String.join(",", allowed.stream().map(s -> "'" + s + "'").toList());

        try {
            String sql = String.format(Locale.ROOT, """
                    select c.id,
                           c.content,
                           ts_rank(c.content_tsv, plainto_tsquery('simple', ?)) as rank
                      from assistant_document_chunk c
                      join assistant_document d on d.id = c.document_id
                     where d.status = 'READY'
                       and c.content_tsv @@ plainto_tsquery('simple', ?)
                       and d.scope in (%s)
                     order by rank desc
                     limit ?
                    """, inClause);
            return jdbc.query(sql,
                    (rs, i) -> new VectorSearchResult(
                            rs.getLong("id"),
                            rs.getString("content"),
                            // Store 1 - rank as pseudo-distance so UI badge coloring still works
                            Math.max(0, 1.0 - rs.getDouble("rank"))),
                    query, query, topK);
        } catch (Exception e) {
            log.warn("[RAG] keywordTopKChunks failed (content_tsv column missing?): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * For each hit, fetch the {@code window} chunks immediately before and after
     * it (same document) and merge them into a single contiguous block. Keeps
     * the top hit's rank/distance but gives the LLM a wider view so it can
     * answer from the full section context — the classic "small chunk for
     * retrieval, big chunk for answering" pattern without a separate index.
     */
    private List<VectorSearchResult> expandWithNeighbors(List<VectorSearchResult> hits, int window) {
        long t0 = System.nanoTime();
        List<VectorSearchResult> expanded = new ArrayList<>(hits.size());
        // Track which chunk ids are already covered to avoid sending the same text twice
        java.util.Set<Long> covered = new java.util.HashSet<>();

        for (VectorSearchResult hit : hits) {
            if (covered.contains(hit.id())) continue;
            try {
                Map<String, Object> info = jdbc.queryForMap(
                        "select document_id, chunk_index from assistant_document_chunk where id = ?", hit.id());
                long docId = ((Number) info.get("document_id")).longValue();
                int idx = ((Number) info.get("chunk_index")).intValue();
                int from = Math.max(0, idx - window);
                int to = idx + window;

                List<Map<String, Object>> rows = jdbc.queryForList("""
                        select id, chunk_index, content
                          from assistant_document_chunk
                         where document_id = ?
                           and chunk_index between ? and ?
                         order by chunk_index asc
                        """, docId, from, to);

                if (rows.isEmpty()) {
                    expanded.add(hit);
                    covered.add(hit.id());
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> r : rows) {
                    Long rowId = ((Number) r.get("id")).longValue();
                    covered.add(rowId);
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(r.get("content"));
                }
                expanded.add(new VectorSearchResult(hit.id(), sb.toString(), hit.distance()));
            } catch (Exception e) {
                log.debug("[RAG] neighbor expansion failed for chunk {}: {}", hit.id(), e.getMessage());
                expanded.add(hit);
                covered.add(hit.id());
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("[RAG] neighbor expansion window=±{} — {} hits → {} expanded blocks ({}ms)",
                window, hits.size(), expanded.size(), ms);
        return expanded;
    }

    public List<VectorSearchResult> searchDocumentChunksByVector(float[] vector, AssistantDocumentScope scope) {
        if (!ragAvailable) {
            log.debug("[RAG] searchDocumentChunksByVector: ragAvailable=false, returning empty");
            return List.of();
        }
        if (vector == null || vector.length == 0 || scope == null) return List.of();
        String literal = toPgVectorLiteral(vector);
        int topK = Math.max(1, runtimeConfig.getVectorTopK());
        double threshold = runtimeConfig.getVectorDistanceThreshold();

        List<String> allowed = new ArrayList<>();
        allowed.add("BOTH");
        if (scope == AssistantDocumentScope.WMS || scope == AssistantDocumentScope.BOTH) allowed.add("WMS");
        if (scope == AssistantDocumentScope.STORE || scope == AssistantDocumentScope.BOTH) allowed.add("STORE");
        String inClause = String.join(",", allowed.stream().map(s -> "'" + s + "'").toList());

        long t0 = System.nanoTime();
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
            List<VectorSearchResult> raw = jdbc.query(sql,
                    (rs, i) -> new VectorSearchResult(
                            rs.getLong("id"),
                            rs.getString("content"),
                            rs.getDouble("distance")),
                    literal, literal, topK);
            List<VectorSearchResult> filtered = raw.stream()
                    .filter(r -> r.distance() <= threshold)
                    .toList();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("[RAG] searchDocumentChunksByVector → {}/{} hits scope={} (threshold={}, topK={}, {}ms)",
                    filtered.size(), raw.size(), scope, threshold, topK, ms);
            for (int i = 0; i < Math.min(filtered.size(), 5); i++) {
                VectorSearchResult r = filtered.get(i);
                log.debug("[RAG]   chunk#{} id={} dist={} preview=\"{}\"",
                        i + 1, r.id(), String.format(Locale.ROOT, "%.4f", r.distance()),
                        truncate(r.content(), 120));
            }
            return filtered;
        } catch (Exception e) {
            log.error("[RAG] Document chunk vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private static String previewQuery(String q) {
        if (q == null) return "null";
        return truncate(q, 140);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
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
