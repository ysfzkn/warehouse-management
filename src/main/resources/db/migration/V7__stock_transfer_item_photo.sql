CREATE TABLE stock_transfer_item_photos (
    id                     BIGSERIAL PRIMARY KEY,
    stock_transfer_item_id BIGINT NOT NULL,
    file_name              VARCHAR(255) NOT NULL,
    relative_path          VARCHAR(500) NOT NULL,
    thumbnail_path         VARCHAR(500) NOT NULL,
    content_type           VARCHAR(100) NOT NULL,
    size_bytes             BIGINT NOT NULL,
    width                  INTEGER,
    height                 INTEGER,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_transfer_item_photo_item
        FOREIGN KEY (stock_transfer_item_id)
        REFERENCES stock_transfer_items (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_stock_transfer_item_photo_item
        UNIQUE (stock_transfer_item_id)
);

CREATE INDEX idx_stock_transfer_item_photo_item
    ON stock_transfer_item_photos (stock_transfer_item_id);


