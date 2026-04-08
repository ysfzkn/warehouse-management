package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Late-init helper for the pgvector-dependent schema.
 * <p>
 * Scenario this solves: the user boots the app against a Postgres that does
 * not have pgvector installed yet. V42 creates the non-vector tables and
 * leaves the vector ones absent. Later, the admin switches to a pgvector
 * image (or installs the extension on their managed Postgres). They don't
 * want to write a new Flyway migration by hand just to bootstrap the vector
 * tables — they call {@link #initializeRagSchema()} from the admin dashboard
 * and this service creates the missing bits idempotently.
 * <p>
 * All statements are guarded with {@code IF NOT EXISTS} so the method is
 * safe to call repeatedly and on a database that already has the full
 * schema.
 */
@Service
public class AssistantRagInitService {

    private static final Logger log = LoggerFactory.getLogger(AssistantRagInitService.class);

    private final JdbcTemplate jdbc;
    private final VectorSearchService vectorSearchService;
    private final AssistantProperties props;

    public AssistantRagInitService(JdbcTemplate jdbc,
                                   VectorSearchService vectorSearchService,
                                   AssistantProperties props) {
        this.jdbc = jdbc;
        this.vectorSearchService = vectorSearchService;
        this.props = props;
    }

    public static class InitResult {
        public boolean extensionInstalled;
        public boolean chunkColumnAdded;
        public boolean productTableCreated;
        public boolean ragNowAvailable;
        public String message;
    }

    /**
     * Attempt to bring the pgvector schema into the expected state. Requires
     * that {@code CREATE EXTENSION vector} is allowed for the current DB user
     * and that pgvector is actually installed on the server.
     */
    @Transactional
    public InitResult initializeRagSchema() {
        InitResult out = new InitResult();
        int dims = props.getRag().getEmbeddingDimensions();

        // 1. Is pgvector available?
        Integer available = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_available_extensions WHERE name = 'vector'", Integer.class);
        if (available == null || available == 0) {
            out.message = "pgvector eklentisi bu Postgres sunucusunda mevcut değil. "
                    + "Docker imajını pgvector/pgvector:pg15 olarak değiştirin veya yönetilen Postgres'te pgvector'ü etkinleştirin.";
            return out;
        }

        // 2. Ensure the extension is created.
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        out.extensionInstalled = true;

        // 3. assistant_document_chunk.embedding
        Integer hasCol = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() "
                        + "AND table_name = 'assistant_document_chunk' AND column_name = 'embedding'",
                Integer.class);
        if (hasCol == null || hasCol == 0) {
            jdbc.execute("ALTER TABLE assistant_document_chunk ADD COLUMN embedding vector(" + dims + ")");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_assistant_chunk_embedding "
                    + "ON assistant_document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)");
            out.chunkColumnAdded = true;
        }

        // 4. product_embedding table
        Integer hasTable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_name = 'product_embedding'",
                Integer.class);
        if (hasTable == null || hasTable == 0) {
            jdbc.execute("CREATE TABLE product_embedding ("
                    + "product_id BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE, "
                    + "content_hash VARCHAR(64) NOT NULL, "
                    + "embedding vector(" + dims + ") NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT NOW())");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_product_embedding_vec "
                    + "ON product_embedding USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)");
            out.productTableCreated = true;
        }

        // 5. Re-detect in the already-running VectorSearchService so subsequent
        //    requests skip the short-circuit without needing a full app restart.
        vectorSearchService.detectPgVector();
        out.ragNowAvailable = vectorSearchService.isRagAvailable();

        out.message = out.ragNowAvailable
                ? "RAG şeması başarıyla oluşturuldu. Ürün embedding backfill'i şimdi çalıştırabilirsiniz."
                : "Şema güncellemeleri uygulandı ancak runtime doğrulaması başarısız. Logları kontrol edin.";
        log.info("RAG schema init result: ext={}, col={}, table={}, available={}",
                out.extensionInstalled, out.chunkColumnAdded, out.productTableCreated, out.ragNowAvailable);
        return out;
    }
}
