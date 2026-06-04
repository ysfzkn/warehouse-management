package com.warehouse.assistant.admin.web;

import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.core.rag.EmbeddingService;
import com.warehouse.assistant.core.rag.VectorSearchResult;
import com.warehouse.assistant.core.rag.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin diagnostics endpoints for evaluating retrieval quality and verifying
 * that embeddings have been generated for all content.
 *
 * <p>All endpoints are read-only and safe to hit from an admin UI while the
 * system is live.
 *
 * <ul>
 *   <li>{@code GET  /api/admin/assistant/diagnostics/stats} — Coverage report:
 *       how many products / documents are embedded, dims, orphans.</li>
 *   <li>{@code POST /api/admin/assistant/diagnostics/retrieve} — Run a query
 *       through the full RAG pipeline and return raw top-K hits with distances
 *       (no LLM in the loop). Great for "is retrieval working?" spot checks.</li>
 *   <li>{@code POST /api/admin/assistant/diagnostics/eval} — Bulk evaluation:
 *       feed a list of {query, expectedId} pairs; get recall@K and MRR.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/assistant/diagnostics")
@Profile("!test")
public class AssistantDiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(AssistantDiagnosticsController.class);

    private final VectorSearchService vectorSearchService;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbc;

    public AssistantDiagnosticsController(VectorSearchService vectorSearchService,
                                          EmbeddingService embeddingService,
                                          JdbcTemplate jdbc) {
        this.vectorSearchService = vectorSearchService;
        this.embeddingService = embeddingService;
        this.jdbc = jdbc;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1) Coverage stats
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ragAvailable", vectorSearchService.isRagAvailable());
        out.put("embeddingServiceAvailable", embeddingService.isAvailable());

        // Products
        Map<String, Object> products = new LinkedHashMap<>();
        products.put("activeTotal", safeCount("SELECT COUNT(*) FROM products WHERE is_active = true"));
        products.put("embedded", safeCount("SELECT COUNT(*) FROM product_embedding"));
        products.put("embeddedActive",
                safeCount("SELECT COUNT(*) FROM product_embedding pe JOIN products p ON p.id = pe.product_id WHERE p.is_active = true"));
        products.put("missing",
                safeCount("""
                        SELECT COUNT(*) FROM products p
                        LEFT JOIN product_embedding pe ON pe.product_id = p.id
                        WHERE p.is_active = true AND pe.product_id IS NULL
                        """));
        products.put("latestIndexedAt",
                safeObject("SELECT MAX(updated_at) FROM product_embedding", Object.class));
        out.put("products", products);

        // Documents
        Map<String, Object> documents = new LinkedHashMap<>();
        documents.put("total", safeCount("SELECT COUNT(*) FROM assistant_document"));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT status, COUNT(*) c FROM assistant_document GROUP BY status",
                    rs -> { byStatus.put(rs.getString(1), rs.getLong(2)); });
        } catch (Exception e) {
            log.warn("[Diag] documents by status query failed: {}", e.getMessage());
        }
        documents.put("byStatus", byStatus);
        documents.put("totalChunks", safeCount("SELECT COUNT(*) FROM assistant_document_chunk"));
        documents.put("embeddedChunks",
                safeCountSilent("SELECT COUNT(*) FROM assistant_document_chunk WHERE embedding IS NOT NULL"));
        documents.put("readyDocuments", safeCount("SELECT COUNT(*) FROM assistant_document WHERE status = 'READY'"));
        documents.put("failedDocuments", safeCount("SELECT COUNT(*) FROM assistant_document WHERE status = 'FAILED'"));
        out.put("documents", documents);

        // Embedding dimension sanity check
        Map<String, Object> dims = new LinkedHashMap<>();
        try {
            Integer productDim = jdbc.queryForObject(
                    "SELECT vector_dims(embedding) FROM product_embedding LIMIT 1", Integer.class);
            dims.put("product", productDim);
        } catch (Exception e) { dims.put("product", null); }
        try {
            Integer chunkDim = jdbc.queryForObject(
                    "SELECT vector_dims(embedding) FROM assistant_document_chunk WHERE embedding IS NOT NULL LIMIT 1",
                    Integer.class);
            dims.put("chunk", chunkDim);
        } catch (Exception e) { dims.put("chunk", null); }
        out.put("embeddingDims", dims);

        return ResponseEntity.ok(out);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2) Live retrieval — see exactly what the LLM will see
    // ──────────────────────────────────────────────────────────────────────

    @PostMapping("/retrieve")
    public ResponseEntity<Map<String, Object>> retrieve(@RequestBody RetrieveRequest body) {
        String query = body != null ? body.query : null;
        String kind = body != null && body.kind != null ? body.kind.toUpperCase() : "PRODUCT";
        String scope = body != null && body.scope != null ? body.scope.toUpperCase() : "STORE";
        boolean ignoreThreshold = body != null && body.ignoreThreshold;
        int topK = body != null && body.topK != null ? Math.max(1, Math.min(50, body.topK)) : 10;

        log.info("[Diag] /retrieve query=\"{}\" kind={} scope={} ignoreThreshold={} topK={}",
                preview(query, 80), kind, scope, ignoreThreshold, topK);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("kind", kind);
        out.put("scope", scope);
        out.put("ignoreThreshold", ignoreThreshold);
        out.put("topK", topK);

        if (query == null || query.isBlank()) {
            out.put("error", "Query boş olamaz.");
            return ResponseEntity.ok(out);
        }

        long t0 = System.nanoTime();
        var vecOpt = embeddingService.embed(query);
        if (vecOpt.isEmpty()) {
            out.put("error", "Embedding servisi başarısız — sağlayıcı/API key ayarlarını kontrol edin.");
            return ResponseEntity.ok(out);
        }
        float[] vec = vecOpt.get();
        long embedMs = (System.nanoTime() - t0) / 1_000_000;
        out.put("embedding", Map.of("dims", vec.length, "elapsedMs", embedMs));

        long t1 = System.nanoTime();
        List<VectorSearchResult> hits;
        if (ignoreThreshold) {
            // Raw top-K — bypass the distance filter in VectorSearchService so admin
            // can see the true distance of every candidate even when nothing passes the threshold.
            hits = rawTopK(vec, kind, scope, topK);
        } else if ("DOCUMENT".equals(kind)) {
            AssistantDocumentScope s;
            try { s = AssistantDocumentScope.valueOf(scope); }
            catch (Exception e) { s = AssistantDocumentScope.STORE; }
            hits = vectorSearchService.searchDocumentChunksByVector(vec, s);
        } else {
            hits = vectorSearchService.searchProductsByVector(vec);
        }
        long searchMs = (System.nanoTime() - t1) / 1_000_000;

        List<Map<String, Object>> hitList = new ArrayList<>(hits.size());
        for (VectorSearchResult r : hits) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("id", r.id());
            h.put("distance", r.distance());
            h.put("similarity", 1.0 - r.distance()); // cosine sim approximation
            h.put("content", preview(r.content(), 300));
            hitList.add(h);
        }
        out.put("hits", hitList);
        out.put("hitCount", hits.size());
        out.put("searchElapsedMs", searchMs);

        // Quick quality signal: min & mean distance
        if (!hits.isEmpty()) {
            double min = hits.stream().mapToDouble(VectorSearchResult::distance).min().orElse(0);
            double mean = hits.stream().mapToDouble(VectorSearchResult::distance).average().orElse(0);
            double max = hits.stream().mapToDouble(VectorSearchResult::distance).max().orElse(0);
            Map<String, Double> dist = new LinkedHashMap<>();
            dist.put("min", round(min));
            dist.put("mean", round(mean));
            dist.put("max", round(max));
            out.put("distanceSummary", dist);
        }

        return ResponseEntity.ok(out);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3) Bulk eval — recall@K, MRR over a golden set
    // ──────────────────────────────────────────────────────────────────────

    @PostMapping("/eval")
    public ResponseEntity<Map<String, Object>> eval(@RequestBody EvalRequest body,
                                                    @RequestParam(name = "topK", defaultValue = "10") int topK) {
        if (body == null || body.cases == null || body.cases.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "cases boş olamaz."));
        }
        String kind = body.kind != null ? body.kind.toUpperCase() : "PRODUCT";
        String scope = body.scope != null ? body.scope.toUpperCase() : "STORE";
        topK = Math.min(50, Math.max(1, topK));

        log.info("[Diag] /eval cases={} kind={} topK={}", body.cases.size(), kind, topK);

        int n = body.cases.size();
        int foundInTop1 = 0;
        int foundInTop3 = 0;
        int foundInTopK = 0;
        double mrr = 0.0;
        List<Map<String, Object>> details = new ArrayList<>(n);

        for (EvalCase c : body.cases) {
            var vecOpt = embeddingService.embed(c.query);
            if (vecOpt.isEmpty()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("query", c.query);
                d.put("expectedId", c.expectedId);
                d.put("error", "embed failed");
                details.add(d);
                continue;
            }
            List<VectorSearchResult> hits;
            if ("DOCUMENT".equals(kind)) {
                AssistantDocumentScope s;
                try { s = AssistantDocumentScope.valueOf(scope); }
                catch (Exception e) { s = AssistantDocumentScope.STORE; }
                hits = vectorSearchService.searchDocumentChunksByVector(vecOpt.get(), s);
            } else {
                hits = vectorSearchService.searchProductsByVector(vecOpt.get());
            }
            // Trim to topK (service may already do this via config but be defensive)
            if (hits.size() > topK) hits = hits.subList(0, topK);

            int rank = -1;
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).id() == c.expectedId) { rank = i + 1; break; }
            }
            if (rank == 1) foundInTop1++;
            if (rank > 0 && rank <= 3) foundInTop3++;
            if (rank > 0) {
                foundInTopK++;
                mrr += 1.0 / rank;
            }

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("query", c.query);
            d.put("expectedId", c.expectedId);
            d.put("rank", rank > 0 ? rank : null);
            d.put("topIds", hits.stream().map(VectorSearchResult::id).toList());
            d.put("topDistances", hits.stream().map(h -> round(h.distance())).toList());
            details.add(d);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", n);
        summary.put("recall@1", round((double) foundInTop1 / n));
        summary.put("recall@3", round((double) foundInTop3 / n));
        summary.put("recall@" + topK, round((double) foundInTopK / n));
        summary.put("mrr", round(mrr / n));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("details", details);
        return ResponseEntity.ok(out);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4) Browse embedded content — chunks (text) and products
    // ──────────────────────────────────────────────────────────────────────

    /** List all documents with chunk + embedding counts so admin can drill in. */
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() {
        try {
            List<Map<String, Object>> rows = jdbc.query("""
                    SELECT d.id, d.title, d.scope, d.status, d.chunk_count, d.indexed_at, d.error_message,
                           (SELECT COUNT(*) FROM assistant_document_chunk c WHERE c.document_id = d.id) AS total_chunks,
                           (SELECT COUNT(*) FROM assistant_document_chunk c
                              WHERE c.document_id = d.id AND c.embedding IS NOT NULL) AS embedded_chunks
                      FROM assistant_document d
                     ORDER BY d.id DESC
                     LIMIT 500
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("title", rs.getString("title"));
                m.put("scope", rs.getString("scope"));
                m.put("status", rs.getString("status"));
                m.put("chunkCount", rs.getObject("chunk_count"));
                m.put("totalChunks", rs.getLong("total_chunks"));
                m.put("embeddedChunks", rs.getLong("embedded_chunks"));
                m.put("indexedAt", rs.getObject("indexed_at"));
                m.put("errorMessage", rs.getString("error_message"));
                return m;
            });
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("[Diag] listDocuments failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Paginated chunk browser with full text. Optionally filter to a single
     * document. If {@code embeddedOnly=true}, hides chunks whose vector is NULL.
     */
    @GetMapping("/chunks")
    public ResponseEntity<Map<String, Object>> listChunks(
            @RequestParam(required = false) Long documentId,
            @RequestParam(defaultValue = "false") boolean embeddedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(100, Math.max(1, size));
        page = Math.max(0, page);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (documentId != null) { where.append(" AND c.document_id = ? "); args.add(documentId); }
        if (embeddedOnly)       { where.append(" AND c.embedding IS NOT NULL "); }

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM assistant_document_chunk c " + where,
                    Long.class, args.toArray());
            out.put("total", total != null ? total : 0);
        } catch (Exception e) { out.put("total", 0); }

        try {
            String sql = """
                    SELECT c.id, c.document_id, c.chunk_index, c.content,
                           (c.embedding IS NOT NULL) AS has_embedding,
                           CASE WHEN c.embedding IS NOT NULL THEN vector_dims(c.embedding) END AS dims,
                           d.title AS document_title, d.scope AS document_scope
                      FROM assistant_document_chunk c
                      JOIN assistant_document d ON d.id = c.document_id
                    """ + where + """
                     ORDER BY c.document_id DESC, c.chunk_index ASC
                     LIMIT ? OFFSET ?
                    """;
            List<Object> params = new ArrayList<>(args);
            params.add(size);
            params.add(page * size);
            List<Map<String, Object>> chunks = jdbc.query(sql, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("documentId", rs.getLong("document_id"));
                m.put("documentTitle", rs.getString("document_title"));
                m.put("documentScope", rs.getString("document_scope"));
                m.put("chunkIndex", rs.getInt("chunk_index"));
                m.put("content", rs.getString("content"));
                m.put("hasEmbedding", rs.getBoolean("has_embedding"));
                m.put("dims", rs.getObject("dims"));
                return m;
            }, params.toArray());
            out.put("chunks", chunks);
        } catch (Exception e) {
            log.error("[Diag] listChunks failed: {}", e.getMessage());
            out.put("chunks", List.of());
        }

        out.put("page", page);
        out.put("size", size);
        return ResponseEntity.ok(out);
    }

    /**
     * Browse the indexed product_embedding table. Useful to verify which
     * products have actually been embedded, when, and to spot stale hashes.
     * With {@code missing=true}, returns active products that lack an embedding.
     */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> listEmbeddedProducts(
            @RequestParam(defaultValue = "false") boolean missing,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(100, Math.max(1, size));
        page = Math.max(0, page);
        String s = search == null ? "" : search.trim();

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            String baseSelect, baseCount;
            List<Object> args = new ArrayList<>();

            if (missing) {
                // Active products WITHOUT an embedding row
                String whereSearch = s.isBlank() ? "" :
                        " AND (p.name ILIKE ? OR p.sku ILIKE ?) ";
                baseCount = """
                        SELECT COUNT(*) FROM products p
                        LEFT JOIN product_embedding pe ON pe.product_id = p.id
                        WHERE p.is_active = true AND pe.product_id IS NULL
                        """ + whereSearch;
                baseSelect = """
                        SELECT p.id, p.name, p.sku, NULL::varchar AS content_hash, NULL::timestamp AS updated_at,
                               false AS has_embedding
                          FROM products p
                          LEFT JOIN product_embedding pe ON pe.product_id = p.id
                         WHERE p.is_active = true AND pe.product_id IS NULL
                        """ + whereSearch + """
                         ORDER BY p.id DESC
                         LIMIT ? OFFSET ?
                        """;
                if (!s.isBlank()) { args.add("%" + s + "%"); args.add("%" + s + "%"); }
            } else {
                // Embedded products
                String whereSearch = s.isBlank() ? "" :
                        " AND (p.name ILIKE ? OR p.sku ILIKE ?) ";
                baseCount = """
                        SELECT COUNT(*) FROM product_embedding pe
                        JOIN products p ON p.id = pe.product_id
                        WHERE 1=1
                        """ + whereSearch;
                baseSelect = """
                        SELECT p.id, p.name, p.sku, pe.content_hash, pe.updated_at,
                               true AS has_embedding
                          FROM product_embedding pe
                          JOIN products p ON p.id = pe.product_id
                         WHERE 1=1
                        """ + whereSearch + """
                         ORDER BY pe.updated_at DESC
                         LIMIT ? OFFSET ?
                        """;
                if (!s.isBlank()) { args.add("%" + s + "%"); args.add("%" + s + "%"); }
            }

            Long total = jdbc.queryForObject(baseCount, Long.class, args.toArray());
            out.put("total", total != null ? total : 0);

            List<Object> params = new ArrayList<>(args);
            params.add(size);
            params.add(page * size);
            List<Map<String, Object>> rows = jdbc.query(baseSelect, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("sku", rs.getString("sku"));
                m.put("contentHash", rs.getString("content_hash"));
                m.put("updatedAt", rs.getObject("updated_at"));
                m.put("hasEmbedding", rs.getBoolean("has_embedding"));
                return m;
            }, params.toArray());
            out.put("products", rows);
        } catch (Exception e) {
            log.error("[Diag] listEmbeddedProducts failed: {}", e.getMessage());
            out.put("products", List.of());
            out.put("total", 0);
        }

        out.put("page", page);
        out.put("size", size);
        out.put("missing", missing);
        return ResponseEntity.ok(out);
    }

    /**
     * Threshold-bypassing top-K retrieval. Returns the K nearest neighbours
     * regardless of cosine distance — useful for diagnosing "I see the chunk
     * in the DB but RAG returns nothing" (usually means the distance is above
     * {@code assistant.rag.vector-distance-threshold}).
     */
    private List<VectorSearchResult> rawTopK(float[] vec, String kind, String scope, int topK) {
        if (vec == null || vec.length == 0) return List.of();
        String literal = VectorSearchService.toPgVectorLiteral(vec);
        try {
            if ("DOCUMENT".equals(kind)) {
                // Scope allowlist like in VectorSearchService
                List<String> allowed = new ArrayList<>();
                allowed.add("BOTH");
                if ("WMS".equals(scope) || "BOTH".equals(scope)) allowed.add("WMS");
                if ("STORE".equals(scope) || "BOTH".equals(scope)) allowed.add("STORE");
                String inClause = String.join(",", allowed.stream().map(a -> "'" + a + "'").toList());
                String sql = String.format(java.util.Locale.ROOT, """
                        select c.id,
                               c.content,
                               (c.embedding <=> ?::vector) as distance
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
                                rs.getLong("id"),
                                rs.getString("content"),
                                rs.getDouble("distance")),
                        literal, literal, topK);
            }
            // PRODUCT
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
                    literal, literal, topK);
        } catch (Exception e) {
            log.error("[Diag] rawTopK failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private long safeCount(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v != null ? v : 0;
        } catch (Exception e) {
            log.debug("[Diag] count failed: {} ({})", sql, e.getMessage());
            return -1;
        }
    }

    private long safeCountSilent(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v != null ? v : 0;
        } catch (Exception e) { return -1; }
    }

    private <T> T safeObject(String sql, Class<T> type) {
        try { return jdbc.queryForObject(sql, type); } catch (Exception e) { return null; }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private static double round(double v) { return Math.round(v * 10_000.0) / 10_000.0; }

    // ── DTOs ──
    public static class RetrieveRequest {
        public String query;
        public String kind;    // PRODUCT | DOCUMENT
        public String scope;   // STORE | WMS | BOTH (for DOCUMENT)
        public Boolean ignoreThreshold;
        public Integer topK;
    }
    public static class EvalRequest {
        public String kind;
        public String scope;
        public List<EvalCase> cases;
    }
    public static class EvalCase {
        public String query;
        public long expectedId;
    }
}
