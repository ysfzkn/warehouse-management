-- =================================================================
-- V20: Wishlists, reviews, coupons, newsletter, CMS, returns
-- Rollback: DROP TABLE return_request_items, return_requests, coupon_usages,
--           coupons, reviews, wishlists, newsletter_subscriptions, cms_pages;
-- =================================================================

-- -------------------------
-- WISHLISTS
-- -------------------------
CREATE TABLE wishlists (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wishlist UNIQUE (customer_id, product_id)
);

CREATE INDEX idx_wishlists_customer ON wishlists (customer_id);
CREATE INDEX idx_wishlists_product ON wishlists (product_id);

-- -------------------------
-- REVIEWS
-- -------------------------
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    order_id        BIGINT REFERENCES orders(id),
    rating          SMALLINT NOT NULL,
    title           VARCHAR(200),
    comment         VARCHAR(2000),
    is_approved     BOOLEAN NOT NULL DEFAULT FALSE,
    admin_reply     VARCHAR(1000),
    admin_reply_at  TIMESTAMP,
    is_visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_review_per_order UNIQUE (customer_id, product_id, order_id)
);

CREATE INDEX idx_reviews_product ON reviews (product_id);
CREATE INDEX idx_reviews_customer ON reviews (customer_id);
CREATE INDEX idx_reviews_approved ON reviews (product_id, is_approved) WHERE is_approved = TRUE;

-- -------------------------
-- COUPONS
-- -------------------------
CREATE TABLE coupons (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(50) NOT NULL,
    description             VARCHAR(500),
    discount_type           VARCHAR(20) NOT NULL,
    discount_value          NUMERIC(10, 2) NOT NULL,
    min_order_amount        NUMERIC(10, 2),
    max_discount_amount     NUMERIC(10, 2),
    usage_limit             INTEGER,
    usage_count             INTEGER NOT NULL DEFAULT 0,
    per_customer_limit      INTEGER NOT NULL DEFAULT 1,
    valid_from              TIMESTAMP NOT NULL,
    valid_until             TIMESTAMP NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    applicable_category_ids BIGINT[],
    applicable_brand_ids    BIGINT[],
    applicable_product_ids  BIGINT[],
    excluded_product_ids    BIGINT[],
    min_item_count          INTEGER,
    created_by              VARCHAR(100),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_coupon_code UNIQUE (code),
    CONSTRAINT chk_coupon_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
    CONSTRAINT chk_coupon_value CHECK (discount_value > 0),
    CONSTRAINT chk_coupon_dates CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupon_percentage CHECK (
        discount_type != 'PERCENTAGE' OR (discount_value > 0 AND discount_value <= 100)
    )
);

CREATE INDEX idx_coupons_code ON coupons (code);
CREATE INDEX idx_coupons_active ON coupons (is_active, valid_from, valid_until) WHERE is_active = TRUE;

CREATE TABLE coupon_usages (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT NOT NULL REFERENCES coupons(id),
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    discount_amount NUMERIC(10, 2) NOT NULL,
    used_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coupon_usages_coupon ON coupon_usages (coupon_id);
CREATE INDEX idx_coupon_usages_customer ON coupon_usages (coupon_id, customer_id);

-- -------------------------
-- NEWSLETTER
-- -------------------------
CREATE TABLE newsletter_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    customer_id     BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    unsubscribe_token VARCHAR(200),
    source          VARCHAR(50) DEFAULT 'WEBSITE',
    subscribed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    CONSTRAINT uq_newsletter_email UNIQUE (email)
);

CREATE INDEX idx_newsletter_active ON newsletter_subscriptions (is_active) WHERE is_active = TRUE;

-- -------------------------
-- CMS PAGES
-- -------------------------
CREATE TABLE cms_pages (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(200) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    meta_title      VARCHAR(200),
    meta_description VARCHAR(500),
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    page_type       VARCHAR(30) NOT NULL DEFAULT 'CONTENT',
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cms_slug UNIQUE (slug),
    CONSTRAINT chk_cms_type CHECK (page_type IN ('CONTENT', 'LEGAL', 'FAQ'))
);

-- Default legal pages
INSERT INTO cms_pages (slug, title, content, page_type, is_published, published_at) VALUES
    ('mesafeli-satis-sozlesmesi', 'Mesafeli Satis Sozlesmesi', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('gizlilik-ve-guvenlik', 'Gizlilik ve Guvenlik Politikasi', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('iptal-ve-iade-sartlari', 'Iptal ve Iade Sartlari', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('kvkk-aydinlatma-metni', 'KVKK Aydinlatma Metni', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('hakkimizda', 'Hakkimizda', '<p>Icerik hazirlanacak</p>', 'CONTENT', TRUE, CURRENT_TIMESTAMP);

-- -------------------------
-- RETURN REQUESTS
-- -------------------------
CREATE TABLE return_requests (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    customer_id         BIGINT NOT NULL REFERENCES customers(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reason              VARCHAR(50) NOT NULL,
    description         VARCHAR(1000),
    cargo_tracking_no   VARCHAR(100),
    cargo_company       VARCHAR(50),
    refund_amount       NUMERIC(12, 2),
    refund_method       VARCHAR(30),
    refunded_at         TIMESTAMP,
    admin_note          VARCHAR(500),
    reviewed_by         VARCHAR(100),
    reviewed_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_return_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'CARGO_WAITING',
        'RECEIVED', 'REFUND_PROCESSING', 'REFUNDED'
    )),
    CONSTRAINT chk_return_reason CHECK (reason IN (
        'DEFECTIVE', 'WRONG_PRODUCT', 'NOT_AS_DESCRIBED',
        'CHANGED_MIND', 'DAMAGED_IN_SHIPPING', 'OTHER'
    ))
);

CREATE INDEX idx_returns_order ON return_requests (order_id);
CREATE INDEX idx_returns_customer ON return_requests (customer_id);
CREATE INDEX idx_returns_status ON return_requests (status);

CREATE TABLE return_request_items (
    id                  BIGSERIAL PRIMARY KEY,
    return_request_id   BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    order_item_id       BIGINT NOT NULL REFERENCES order_items(id),
    quantity            INTEGER NOT NULL,
    reason              VARCHAR(500),
    photo_urls          TEXT[],
    CONSTRAINT chk_return_item_qty CHECK (quantity >= 1)
);

CREATE INDEX idx_return_items_request ON return_request_items (return_request_id);
