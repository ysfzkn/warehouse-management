-- Initial schema extracted from current JPA model

-- Master data tables
CREATE TABLE brands (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  VARCHAR(255),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE colors (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    hex_code     VARCHAR(7),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE warehouses (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    location      VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    manager       VARCHAR(100),
    capacity_sqm  DOUBLE PRECISION,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    parent_id   BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id)
        REFERENCES categories (id)
        ON DELETE SET NULL
);

CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    sku            VARCHAR(50) NOT NULL UNIQUE,
    price          NUMERIC(10, 2),
    weight         DOUBLE PRECISION,
    dimensions     VARCHAR(50),
    length_cm      DOUBLE PRECISION,
    width_cm       DOUBLE PRECISION,
    height_cm      DOUBLE PRECISION,
    shipping_rate  NUMERIC(10, 2),
    vat_rate       NUMERIC(5, 2),
    sct_rate       NUMERIC(5, 2),
    category_id    BIGINT NOT NULL,
    brand_id       BIGINT,
    color_id       BIGINT,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_products_price_non_negative CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_products_shipping_non_negative CHECK (shipping_rate IS NULL OR shipping_rate >= 0),
    CONSTRAINT chk_products_weight_non_negative CHECK (weight IS NULL OR weight >= 0),
    CONSTRAINT chk_products_length_non_negative CHECK (length_cm IS NULL OR length_cm >= 0),
    CONSTRAINT chk_products_width_non_negative CHECK (width_cm IS NULL OR width_cm >= 0),
    CONSTRAINT chk_products_height_non_negative CHECK (height_cm IS NULL OR height_cm >= 0),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id)
        REFERENCES brands (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_products_color FOREIGN KEY (color_id)
        REFERENCES colors (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_is_active ON products (is_active);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_products_color_id ON products (color_id);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(120) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    action      VARCHAR(40) NOT NULL,
    entity_type VARCHAR(60),
    entity_id   BIGINT,
    username    VARCHAR(255) NOT NULL,
    details     VARCHAR(2000),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_username ON audit_logs (username);
CREATE INDEX idx_audit_action ON audit_logs (action);

CREATE TABLE notifications (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(140) NOT NULL,
    message        VARCHAR(2000) NOT NULL,
    is_read        BOOLEAN NOT NULL DEFAULT FALSE,
    target_user_id BIGINT,
    entity_type    VARCHAR(60),
    entity_id      BIGINT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_created_at ON notifications (created_at);
CREATE INDEX idx_notif_read ON notifications (is_read);

CREATE TABLE stocks (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    warehouse_id        BIGINT NOT NULL,
    quantity            INTEGER NOT NULL DEFAULT 0,
    min_stock_level     INTEGER DEFAULT 0,
    reserved_quantity   INTEGER DEFAULT 0,
    consigned_quantity  INTEGER DEFAULT 0,
    last_updated        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stocks_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_stocks_min_level_non_negative CHECK (min_stock_level IS NULL OR min_stock_level >= 0),
    CONSTRAINT chk_stocks_reserved_non_negative CHECK (reserved_quantity IS NULL OR reserved_quantity >= 0),
    CONSTRAINT chk_stocks_consigned_non_negative CHECK (consigned_quantity IS NULL OR consigned_quantity >= 0),
    CONSTRAINT fk_stocks_product FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_stocks_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_stocks_product_warehouse UNIQUE (product_id, warehouse_id)
);

CREATE INDEX idx_stocks_product_id ON stocks (product_id);
CREATE INDEX idx_stocks_warehouse_id ON stocks (warehouse_id);
CREATE INDEX idx_stocks_last_updated ON stocks (last_updated);
CREATE INDEX idx_stocks_quantity ON stocks (quantity);

CREATE TABLE stock_requests (
    id               BIGSERIAL PRIMARY KEY,
    stock_id         BIGINT NOT NULL,
    type             VARCHAR(20) NOT NULL,
    quantity         INTEGER NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by     VARCHAR(100) NOT NULL,
    requested_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by      VARCHAR(100),
    reviewed_at      TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    notes            VARCHAR(1000),
    CONSTRAINT chk_stock_requests_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_stock_requests_stock FOREIGN KEY (stock_id)
        REFERENCES stocks (id)
        ON DELETE CASCADE
);

CREATE TABLE stock_transfers (
    id                         BIGSERIAL PRIMARY KEY,
    source_warehouse_id        BIGINT NOT NULL,
    destination_warehouse_id   BIGINT,
    product_id                 BIGINT,
    quantity                   INTEGER NOT NULL,
    driver_name                VARCHAR(100) NOT NULL,
    driver_tc_id               CHAR(11) NOT NULL,
    driver_phone               VARCHAR(20) NOT NULL,
    vehicle_plate              VARCHAR(20) NOT NULL,
    status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transfer_type              VARCHAR(40) NOT NULL DEFAULT 'WAREHOUSE',
    customer_full_name         VARCHAR(150),
    customer_phone             VARCHAR(20),
    customer_address           VARCHAR(500),
    transfer_date              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_date             TIMESTAMP,
    cancelled_date             TIMESTAMP,
    notes                      VARCHAR(500),
    cancellation_reason        VARCHAR(500),
    created_by                 VARCHAR(100),
    completion_note            VARCHAR(500),
    created_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_transfers_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT fk_stock_transfers_source FOREIGN KEY (source_warehouse_id)
        REFERENCES warehouses (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_stock_transfers_destination FOREIGN KEY (destination_warehouse_id)
        REFERENCES warehouses (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_stock_transfers_product FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE SET NULL
);

CREATE TABLE stock_transfer_items (
    id                 BIGSERIAL PRIMARY KEY,
    stock_transfer_id  BIGINT NOT NULL,
    product_id         BIGINT NOT NULL,
    quantity           INTEGER NOT NULL,
    CONSTRAINT chk_transfer_items_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (stock_transfer_id)
        REFERENCES stock_transfers (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_transfer_items_product FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_stock_transfer_items_transfer ON stock_transfer_items (stock_transfer_id);
CREATE INDEX idx_stock_transfer_items_product ON stock_transfer_items (product_id);

CREATE TABLE stock_import_history (
    id                 BIGSERIAL PRIMARY KEY,
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL,
    content_type       VARCHAR(255) NOT NULL,
    warehouse_id       BIGINT NOT NULL,
    total_rows         INTEGER,
    created_products   INTEGER,
    updated_products   INTEGER,
    created_categories INTEGER,
    created_stocks     INTEGER,
    updated_stocks     INTEGER,
    status             VARCHAR(30),
    error_message      VARCHAR(2000),
    failed_rows        TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_import_history_stored UNIQUE (stored_filename),
    CONSTRAINT fk_stock_import_history_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses (id)
        ON DELETE CASCADE
);

