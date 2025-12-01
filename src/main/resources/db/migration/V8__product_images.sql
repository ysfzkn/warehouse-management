CREATE TABLE product_images (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    relative_path  VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    width          INTEGER,
    height         INTEGER,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    is_primary     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_product_images_product_id
    ON product_images (product_id);


