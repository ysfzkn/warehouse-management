-- Hybrid search: PostgreSQL full-text (BM25-ish) indexes alongside pgvector.
-- Query-time: RAG pipeline runs both vector similarity and ts_rank scoring,
-- then fuses the two ranked lists via Reciprocal Rank Fusion (RRF).
--
-- Why "simple" not "turkish":
--   The 'turkish' text search config exists in Postgres 15+ but its stemmer is
--   limited (doesn't handle suffix-heavy Turkish morphology very well). The
--   'simple' config indexes every lexeme literally — better recall for legal /
--   policy documents where exact term matches (e.g. "MADDE 6", "KVKK") matter
--   more than aggressive stemming.

-- 1) Document chunks: add generated tsvector column + GIN index
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'assistant_document_chunk'
          AND column_name = 'content_tsv'
    ) THEN
        EXECUTE 'ALTER TABLE assistant_document_chunk '
             || 'ADD COLUMN content_tsv tsvector '
             || 'GENERATED ALWAYS AS (to_tsvector(''simple'', coalesce(content, ''''))) STORED';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv
    ON assistant_document_chunk
    USING GIN (content_tsv);

-- 2) Products: expression index on name + description (skip if products table
--    doesn't have description column — adjust accordingly).
DO $$
DECLARE
    has_description boolean;
BEGIN
    SELECT EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'products'
          AND column_name = 'description'
    ) INTO has_description;

    IF has_description THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_search_tsv '
             || 'ON products USING GIN ('
             || '  to_tsvector(''simple'', coalesce(name, '''') || '' '' || coalesce(description, '''')))';
    ELSE
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_search_tsv '
             || 'ON products USING GIN (to_tsvector(''simple'', coalesce(name, '''')))';
    END IF;
END $$;

-- 3) Register RAG hybrid knobs so the admin UI surfaces them
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('assistant.rag.hybrid-enabled', 'true', 'BOOLEAN'),
    ('assistant.rag.hybrid-rrf-k', '60', 'NUMBER')
ON CONFLICT (setting_key) DO NOTHING;
