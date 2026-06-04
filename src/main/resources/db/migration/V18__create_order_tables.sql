-- =================================================================
-- V18: Orders, order items, order status history
-- Rollback: DROP TABLE order_status_history, order_items, orders; DROP SEQUENCE order_number_seq;
-- =================================================================

CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id                      BIGSERIAL PRIMARY KEY,
    order_number            VARCHAR(20) NOT NULL,
    customer_id             BIGINT NOT NULL REFERENCES customers(id),

    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',

    shipping_address_snapshot   JSONB NOT NULL,
    billing_address_snapshot    JSONB NOT NULL,

    subtotal                NUMERIC(12, 2) NOT NULL,
    shipping_cost           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    discount_amount         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    vat_total               NUMERIC(10, 2) NOT NULL DEFAULT 0,
    sct_total               NUMERIC(10, 2) NOT NULL DEFAULT 0,
    grand_total             NUMERIC(12, 2) NOT NULL,

    coupon_id               BIGINT,
    coupon_code             VARCHAR(50),
    coupon_discount         NUMERIC(10, 2),

    payment_method          VARCHAR(30),
    installment_count       INTEGER NOT NULL DEFAULT 1,

    cargo_company           VARCHAR(50),
    cargo_tracking_no       VARCHAR(100),
    estimated_delivery_date DATE,
    actual_delivery_date    DATE,

    stock_transfer_id       BIGINT REFERENCES stock_transfers(id) ON DELETE SET NULL,

    customer_note           VARCHAR(500),
    admin_note              VARCHAR(500),
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(500),

    distance_sales_contract_accepted    BOOLEAN NOT NULL DEFAULT FALSE,
    distance_sales_contract_accepted_at TIMESTAMP,

    invoice_number          VARCHAR(50),
    invoice_url             VARCHAR(500),

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_order_number UNIQUE (order_number),
    CONSTRAINT chk_order_status CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED',
        'CANCELLED', 'RETURN_REQUESTED', 'RETURNED', 'REFUNDED'
    )),
    CONSTRAINT chk_order_payment CHECK (payment_method IS NULL OR payment_method IN (
        'CREDIT_CARD', 'DOOR_CASH', 'DOOR_CARD'
    )),
    CONSTRAINT chk_order_cargo CHECK (cargo_company IS NULL OR cargo_company IN (
        'YURTICI', 'ARAS', 'MNG', 'PTT', 'SURAT', 'UPS', 'OTHER'
    )),
    CONSTRAINT chk_order_totals CHECK (subtotal >= 0 AND grand_total >= 0)
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_number ON orders (order_number);
CREATE INDEX idx_orders_created ON orders (created_at DESC);
CREATE INDEX idx_orders_transfer ON orders (stock_transfer_id) WHERE stock_transfer_id IS NOT NULL;

CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    product_snapshot    JSONB NOT NULL,
    quantity            INTEGER NOT NULL,
    unit_price          NUMERIC(10, 2) NOT NULL,
    vat_rate            NUMERIC(5, 2) NOT NULL,
    sct_rate            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(10, 2) NOT NULL,
    warehouse_id        BIGINT REFERENCES warehouses(id),
    stock_id            BIGINT REFERENCES stocks(id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_order_item_qty CHECK (quantity >= 1),
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_item_total CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status      VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    changed_by      VARCHAR(100),
    change_source   VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    note            VARCHAR(500),
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_history_order ON order_status_history (order_id);
CREATE INDEX idx_order_history_created ON order_status_history (created_at DESC);
