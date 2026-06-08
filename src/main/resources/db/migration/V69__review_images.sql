-- Photos attached to product reviews (uploaded by the customer with their review).
-- Stored via the generic document storage (storeDocument) — storage_key is the
-- reopenable key. Deleting a review cascades to its images.

CREATE TABLE IF NOT EXISTS review_images (
    id           BIGSERIAL PRIMARY KEY,
    review_id    BIGINT       NOT NULL,
    file_name    VARCHAR(255),
    storage_key  VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_review_images_review ON review_images (review_id);
