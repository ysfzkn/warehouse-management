-- =============================================================================
-- V42: Assistant core schema (Cezeri v2)
-- =============================================================================
-- Introduces persistent storage for the unified assistant platform (WMS and
-- Store profiles). The migration is designed to be pgvector-OPTIONAL: if the
-- `vector` extension is not installed on the target PostgreSQL server, the
-- non-vector parts are still created and the application boots cleanly with
-- RAG features automatically disabled at runtime.
--
-- Rationale: Railway's default Postgres template and most vanilla local
-- installs do NOT bundle pgvector. Failing the migration would take down the
-- whole app — including the WMS assistant, which doesn't even need RAG.
-- Instead we degrade gracefully and tell the admin to install pgvector
-- (Docker image: pgvector/pgvector:pg15 (or :pg17 if starting from a clean volume)) when they want product semantic
-- search or FAQ document retrieval.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. pgvector extension (best-effort)
-- -----------------------------------------------------------------------------
-- We check pg_available_extensions first — it lists extensions whose control
-- file is present on the server disk. If pgvector isn't even AVAILABLE we
-- don't try to CREATE EXTENSION (which would throw SQLSTATE 0A000 and, in
-- some PostgreSQL versions, abort the whole DO block despite the EXCEPTION
-- handler).
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        RAISE NOTICE 'pgvector extension is available — RAG features will be enabled.';
    ELSE
        RAISE NOTICE 'pgvector NOT available on this PostgreSQL server. '
                     'Assistant RAG features will be disabled at runtime. '
                     'To enable: switch to the pgvector/pgvector:pg15 (or :pg17 if starting from a clean volume) Docker image '
                     'or install the pgvector extension into your managed Postgres.';
    END IF;
END $$;


-- -----------------------------------------------------------------------------
-- 2. assistant_conversation (always created — no vector columns)
-- -----------------------------------------------------------------------------
CREATE TABLE assistant_conversation (
    id                      BIGSERIAL       PRIMARY KEY,
    profile                 VARCHAR(16)     NOT NULL,                   -- WMS | STORE
    user_id                 BIGINT          NULL,
    customer_id             BIGINT          NULL,
    guest_session_id        VARCHAR(64)     NULL,
    username                VARCHAR(128)    NULL,
    started_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_activity_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    message_count           INTEGER         NOT NULL DEFAULT 0,
    total_prompt_tokens     BIGINT          NOT NULL DEFAULT 0,
    total_completion_tokens BIGINT          NOT NULL DEFAULT 0,
    total_cost_usd          NUMERIC(12, 6)  NOT NULL DEFAULT 0,
    ip_hash                 VARCHAR(64)     NULL,
    user_agent              VARCHAR(512)    NULL,
    CONSTRAINT chk_assistant_conv_profile CHECK (profile IN ('WMS', 'STORE')),
    CONSTRAINT chk_assistant_conv_actor   CHECK (
        (user_id IS NOT NULL) OR (customer_id IS NOT NULL) OR (guest_session_id IS NOT NULL)
    )
);

    CREATE INDEX idx_assistant_conv_profile_started
    ON assistant_conversation (profile, started_at DESC);

CREATE INDEX idx_assistant_conv_user
    ON assistant_conversation (user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_assistant_conv_customer
    ON assistant_conversation (customer_id)
    WHERE customer_id IS NOT NULL;

CREATE INDEX idx_assistant_conv_guest
    ON assistant_conversation (guest_session_id)
    WHERE guest_session_id IS NOT NULL;


-- -----------------------------------------------------------------------------
-- 3. assistant_message (always created)
-- -----------------------------------------------------------------------------
CREATE TABLE assistant_message (
    id                BIGSERIAL       PRIMARY KEY,
    conversation_id   BIGINT          NOT NULL
                                      REFERENCES assistant_conversation(id) ON DELETE CASCADE,
    role              VARCHAR(16)     NOT NULL,
    content           TEXT            NOT NULL,
    tool_calls        JSONB           NULL,
    prompt_tokens     INTEGER         NULL,
    completion_tokens INTEGER         NULL,
    latency_ms        INTEGER         NULL,
    cost_usd          NUMERIC(10, 6)  NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_assistant_msg_role CHECK (role IN ('user', 'assistant', 'tool', 'system'))
);

CREATE INDEX idx_assistant_msg_conv_created
    ON assistant_message (conversation_id, created_at);


-- -----------------------------------------------------------------------------
-- 4. assistant_document (always created)
-- -----------------------------------------------------------------------------
CREATE TABLE assistant_document (
    id            BIGSERIAL       PRIMARY KEY,
    title         VARCHAR(255)    NOT NULL,
    scope         VARCHAR(16)     NOT NULL,
    file_name     VARCHAR(255)    NOT NULL,
    storage_path  VARCHAR(512)    NOT NULL,
    mime_type     VARCHAR(128)    NULL,
    size_bytes    BIGINT          NOT NULL,
    uploaded_by   VARCHAR(128)    NULL,
    chunk_count   INTEGER         NOT NULL DEFAULT 0,
    status        VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1024)   NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    indexed_at    TIMESTAMP       NULL,
    CONSTRAINT chk_assistant_doc_scope  CHECK (scope  IN ('WMS', 'STORE', 'BOTH')),
    CONSTRAINT chk_assistant_doc_status CHECK (status IN ('PENDING', 'INDEXING', 'READY', 'FAILED'))
);

CREATE INDEX idx_assistant_doc_scope_status
    ON assistant_document (scope, status);


-- -----------------------------------------------------------------------------
-- 5. assistant_document_chunk (metadata always; embedding column conditional)
-- -----------------------------------------------------------------------------
CREATE TABLE assistant_document_chunk (
    id           BIGSERIAL      PRIMARY KEY,
    document_id  BIGINT         NOT NULL
                                REFERENCES assistant_document(id) ON DELETE CASCADE,
    chunk_index  INTEGER        NOT NULL,
    content      TEXT           NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_assistant_chunk_doc_idx UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_assistant_chunk_document
    ON assistant_document_chunk (document_id);


-- -----------------------------------------------------------------------------
-- 6. Vector columns + product_embedding table (conditional on pgvector)
-- -----------------------------------------------------------------------------
-- Only runs if step 1 successfully installed the extension. Otherwise both
-- the `embedding` column and the `product_embedding` table remain absent,
-- and VectorSearchService silently disables itself at runtime. Running this
-- DO block later (via a future migration) after installing pgvector will
-- retroactively enable the RAG pipeline.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 6a. add embedding column to existing chunk table
        EXECUTE 'ALTER TABLE assistant_document_chunk ADD COLUMN embedding vector(1536)';

        EXECUTE 'CREATE INDEX idx_assistant_chunk_embedding '
             || 'ON assistant_document_chunk '
             || 'USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)';

        -- 6b. product_embedding table (skipped entirely when pgvector is absent)
        EXECUTE '
            CREATE TABLE product_embedding (
                product_id   BIGINT         PRIMARY KEY
                                            REFERENCES products(id) ON DELETE CASCADE,
                content_hash VARCHAR(64)    NOT NULL,
                embedding    vector(1536)   NOT NULL,
                updated_at   TIMESTAMP      NOT NULL DEFAULT NOW()
            )';

        EXECUTE 'CREATE INDEX idx_product_embedding_vec '
             || 'ON product_embedding '
             || 'USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)';

        RAISE NOTICE 'pgvector tables + indexes created.';
    ELSE
        RAISE NOTICE 'Skipping pgvector-dependent tables. RAG will be disabled at runtime.';
    END IF;
END $$;
