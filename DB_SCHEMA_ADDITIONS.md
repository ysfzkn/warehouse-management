# Database Schema Additions: E-Commerce Layer

## 1. Genel Bakis

Mevcut veritabani 14 Flyway migrasyonu ile olusturulmustur (V1-V14). E-ticaret katmani icin V15-V20 migrasyonlari eklenecektir.

### Mevcut Tablolar (Degistirilecek)
- `products` - slug, SEO alanlari, indirim alanlari eklenir
- `categories` - slug, gorsel, menu ayarlari eklenir
- `brands` - slug, logo eklenir

### Yeni Tablolar
- `customers` - Musteri hesaplari
- `customer_addresses` - Teslimat/fatura adresleri
- `carts` + `cart_items` - Alisveris sepeti
- `orders` + `order_items` + `order_status_history` - Siparisler
- `payments` - Odeme kayitlari
- `wishlists` - Favori urunler
- `reviews` - Urun degerlendirmeleri
- `coupons` + `coupon_usages` - Kupon/indirim sistemi
- `newsletter_subscriptions` - Bulten abonelikleri
- `cms_pages` - Statik icerik sayfalari
- `return_requests` + `return_request_items` - Iade talepleri
- `customer_refresh_tokens` - Refresh token yonetimi

---

## 2. Entity-Relationship Diyagrami

```
                    +-------------+
                    |  customers  |
                    +------+------+
                           |
          +----------------+----------------+------------------+
          |                |                |                  |
+---------+------+ +-------+------+ +-------+------+ +--------+-------+
| customer_      | |    carts     | |   orders     | |   wishlists    |
| addresses      | +-------+------+ +-------+------+ +----------------+
+----------------+         |                |
                   +-------+------+ +-------+------+------------------+
                   |  cart_items  | | order_items  | | order_status_  |
                   +------+------+ +------+------+ |   history       |
                          |               |        +------------------+
                          |               |
                    +-----+-----+   +-----+-----+
                    |  products  |   |  payments  |
                    +-----------+   +-----------+
                          |
                    +-----+-----+
                    |  reviews   |
                    +-----------+

+-------------+     +----------------+     +------------------+
|   coupons   +---->| coupon_usages  |     | return_requests  |
+-------------+     +----------------+     +--------+---------+
                                                    |
                                           +--------+---------+
                                           | return_request_  |
                                           |     items        |
                                           +------------------+

+--------------------+     +-----------+
| newsletter_        |     | cms_pages |
| subscriptions      |     +-----------+
+--------------------+
```

---

## 3. Migrasyon Detaylari

### V15 - Mevcut Tablolara E-Ticaret Alanlari

```sql
-- =================================================================
-- V15__add_ecommerce_fields_to_existing_tables.sql
-- Mevcut products, categories, brands tablolarina e-ticaret alanlari
-- =================================================================

-- -------------------------
-- PRODUCTS: Slug + SEO + Indirim
-- -------------------------
ALTER TABLE products ADD COLUMN slug VARCHAR(200);
ALTER TABLE products ADD COLUMN meta_title VARCHAR(200);
ALTER TABLE products ADD COLUMN meta_description VARCHAR(500);
ALTER TABLE products ADD COLUMN short_description VARCHAR(1000);
ALTER TABLE products ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN is_new BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN sale_price NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN sale_start TIMESTAMP;
ALTER TABLE products ADD COLUMN sale_end TIMESTAMP;
ALTER TABLE products ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN sold_count INTEGER NOT NULL DEFAULT 0;

-- Slug unique index (NULL'lar icin partial index)
CREATE UNIQUE INDEX idx_products_slug ON products (slug) WHERE slug IS NOT NULL;
CREATE INDEX idx_products_featured ON products (is_featured) WHERE is_featured = TRUE;
CREATE INDEX idx_products_new ON products (is_new) WHERE is_new = TRUE;
CREATE INDEX idx_products_sale ON products (sale_start, sale_end) WHERE sale_price IS NOT NULL;

-- Mevcut urunler icin slug uret (id bazli gecici slug)
UPDATE products SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

-- Slug zorunlu yap
ALTER TABLE products ALTER COLUMN slug SET NOT NULL;

-- -------------------------
-- CATEGORIES: Slug + Menu Ayarlari
-- -------------------------
ALTER TABLE categories ADD COLUMN slug VARCHAR(100);
ALTER TABLE categories ADD COLUMN image_url VARCHAR(500);
ALTER TABLE categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE categories ADD COLUMN show_in_menu BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE categories ADD COLUMN meta_title VARCHAR(200);
ALTER TABLE categories ADD COLUMN meta_description VARCHAR(500);

-- Mevcut kategoriler icin slug uret
UPDATE categories SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX idx_categories_slug ON categories (slug);

-- -------------------------
-- BRANDS: Slug + Logo
-- -------------------------
ALTER TABLE brands ADD COLUMN slug VARCHAR(100);
ALTER TABLE brands ADD COLUMN logo_url VARCHAR(500);

UPDATE brands SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

ALTER TABLE brands ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX idx_brands_slug ON brands (slug);
```

---

### V16 - Musteri ve Adres Tablolari

```sql
-- =================================================================
-- V16__create_customer_tables.sql
-- Musteri hesaplari ve adres yonetimi
-- =================================================================

-- -------------------------
-- CUSTOMERS
-- -------------------------
CREATE TABLE customers (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(120) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    tc_kimlik_no        VARCHAR(11),
    gender              VARCHAR(10),              -- MALE, FEMALE, OTHER
    birth_date          DATE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    email_verify_token  VARCHAR(200),
    email_verify_sent_at TIMESTAMP,
    kvkk_consent        BOOLEAN NOT NULL DEFAULT FALSE,
    kvkk_consent_at     TIMESTAMP,
    marketing_consent   BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_consent_at TIMESTAMP,
    password_reset_token VARCHAR(200),
    password_reset_sent_at TIMESTAMP,
    last_login_at       TIMESTAMP,
    last_login_ip       VARCHAR(45),
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT chk_customers_tc CHECK (tc_kimlik_no IS NULL OR LENGTH(tc_kimlik_no) = 11)
);

CREATE INDEX idx_customers_email ON customers (email);
CREATE INDEX idx_customers_phone ON customers (phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_customers_active ON customers (is_active) WHERE is_active = TRUE;

-- -------------------------
-- CUSTOMER REFRESH TOKENS
-- -------------------------
CREATE TABLE customer_refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_customer ON customer_refresh_tokens (customer_id);
CREATE INDEX idx_refresh_tokens_token ON customer_refresh_tokens (token);

-- -------------------------
-- CUSTOMER ADDRESSES
-- -------------------------
CREATE TABLE customer_addresses (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    title           VARCHAR(100) NOT NULL,          -- "Ev Adresim", "Is Adresim"
    address_type    VARCHAR(20) NOT NULL DEFAULT 'SHIPPING',  -- SHIPPING, BILLING, BOTH
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    city            VARCHAR(100) NOT NULL,          -- Il (Istanbul, Ankara, ...)
    district        VARCHAR(100) NOT NULL,          -- Ilce (Kadikoy, Cankaya, ...)
    neighborhood    VARCHAR(200),                   -- Mahalle
    address_line    VARCHAR(500) NOT NULL,           -- Tam adres
    postal_code     VARCHAR(10),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,

    -- Fatura bilgileri (kurumsal)
    tc_kimlik_no    VARCHAR(11),                    -- Bireysel fatura
    company_name    VARCHAR(200),                   -- Kurumsal fatura
    tax_office      VARCHAR(200),                   -- Vergi dairesi
    tax_number      VARCHAR(20),                    -- Vergi numarasi

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_address_type CHECK (address_type IN ('SHIPPING', 'BILLING', 'BOTH'))
);

CREATE INDEX idx_customer_addresses_customer ON customer_addresses (customer_id);
CREATE INDEX idx_customer_addresses_default ON customer_addresses (customer_id, is_default) WHERE is_default = TRUE;
```

---

### V17 - Sepet Tablolari

```sql
-- =================================================================
-- V17__create_cart_tables.sql
-- Server-side persistent alisveris sepeti
-- =================================================================

CREATE TABLE carts (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT REFERENCES customers(id) ON DELETE CASCADE,
    session_id      VARCHAR(100),                   -- Guest kullanicilar icin session ID
    coupon_id       BIGINT,                         -- Uygulanan kupon (FK V20'de eklenir)
    expires_at      TIMESTAMP,                      -- Guest cart expiry (30 gun)
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Bir customer'in tek aktif sepeti olabilir
    CONSTRAINT uq_cart_customer UNIQUE (customer_id),
    -- Bir session'in tek aktif sepeti olabilir
    CONSTRAINT uq_cart_session UNIQUE (session_id),
    -- En az biri dolu olmali
    CONSTRAINT chk_cart_owner CHECK (customer_id IS NOT NULL OR session_id IS NOT NULL)
);

CREATE INDEX idx_carts_customer ON carts (customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX idx_carts_session ON carts (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_carts_expires ON carts (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE cart_items (
    id              BIGSERIAL PRIMARY KEY,
    cart_id         BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity        INTEGER NOT NULL DEFAULT 1,
    added_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cart_item_qty CHECK (quantity >= 1 AND quantity <= 100),
    -- Ayni urun sepette bir kez
    CONSTRAINT uq_cart_item_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);
CREATE INDEX idx_cart_items_product ON cart_items (product_id);
```

---

### V18 - Siparis Tablolari

```sql
-- =================================================================
-- V18__create_order_tables.sql
-- Siparis yonetimi
-- =================================================================

-- -------------------------
-- ORDERS (Ana siparis tablosu)
-- -------------------------
CREATE TABLE orders (
    id                      BIGSERIAL PRIMARY KEY,
    order_number            VARCHAR(20) NOT NULL,       -- "ORD-20260331-0001"
    customer_id             BIGINT NOT NULL REFERENCES customers(id),

    -- Siparis durumu
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    -- Durum akisi:
    -- PENDING_PAYMENT -> PAID -> PREPARING -> SHIPPED -> DELIVERED
    -- (herhangi bir noktada) -> CANCELLED
    -- DELIVERED -> RETURN_REQUESTED -> RETURNED -> REFUNDED

    -- Adres snapshot'lari (siparis anindaki kopya, JSON)
    shipping_address_snapshot   JSONB NOT NULL,
    billing_address_snapshot    JSONB NOT NULL,

    -- Fiyat kirilimi
    subtotal                NUMERIC(12, 2) NOT NULL,    -- Urun toplami (KDV haric)
    shipping_cost           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    discount_amount         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    vat_total               NUMERIC(10, 2) NOT NULL DEFAULT 0,  -- Toplam KDV
    sct_total               NUMERIC(10, 2) NOT NULL DEFAULT 0,  -- Toplam OTV
    grand_total             NUMERIC(12, 2) NOT NULL,    -- Genel toplam

    -- Kupon bilgisi
    coupon_id               BIGINT,
    coupon_code             VARCHAR(50),
    coupon_discount         NUMERIC(10, 2),

    -- Odeme bilgisi
    payment_method          VARCHAR(30),                -- CREDIT_CARD, DOOR_CASH, DOOR_CARD
    installment_count       INTEGER NOT NULL DEFAULT 1,

    -- Kargo bilgisi
    cargo_company           VARCHAR(50),                -- YURTICI, ARAS, MNG, PTT
    cargo_tracking_no       VARCHAR(100),
    estimated_delivery_date DATE,
    actual_delivery_date    DATE,

    -- WMS entegrasyonu
    stock_transfer_id       BIGINT REFERENCES stock_transfers(id) ON DELETE SET NULL,

    -- Musteri bilgileri
    customer_note           VARCHAR(500),
    admin_note              VARCHAR(500),
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(500),

    -- Yasal
    distance_sales_contract_accepted    BOOLEAN NOT NULL DEFAULT FALSE,
    distance_sales_contract_accepted_at TIMESTAMP,

    -- Fatura
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
    ))
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_number ON orders (order_number);
CREATE INDEX idx_orders_created ON orders (created_at DESC);
CREATE INDEX idx_orders_transfer ON orders (stock_transfer_id) WHERE stock_transfer_id IS NOT NULL;

-- -------------------------
-- ORDER ITEMS (Siparis kalemleri)
-- -------------------------
CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),

    -- Siparis anindaki urun snapshot'i
    product_snapshot    JSONB NOT NULL,
    -- Ornek: {"name": "...", "sku": "...", "imageUrl": "...", "brand": "...", "color": "..."}

    quantity            INTEGER NOT NULL,
    unit_price          NUMERIC(10, 2) NOT NULL,     -- Birim fiyat (KDV haric)
    vat_rate            NUMERIC(5, 2) NOT NULL,      -- KDV orani (%)
    sct_rate            NUMERIC(5, 2) NOT NULL DEFAULT 0,  -- OTV orani (%)
    discount_amount     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(10, 2) NOT NULL,     -- Satir toplami (vergiler dahil)

    -- Stok referansi
    warehouse_id        BIGINT REFERENCES warehouses(id),  -- Hangi depodan gonderildi
    stock_id            BIGINT REFERENCES stocks(id),      -- Stok kaydi referansi

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_order_item_qty CHECK (quantity >= 1),
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_item_total CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

-- -------------------------
-- ORDER STATUS HISTORY (Durum gecmisi)
-- -------------------------
CREATE TABLE order_status_history (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status      VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    changed_by      VARCHAR(100),                   -- admin username, "system", veya "customer"
    change_source   VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',  -- ADMIN, SYSTEM, CUSTOMER, PAYMENT_CALLBACK
    note            VARCHAR(500),
    metadata        JSONB,                          -- Ek bilgi (kargo no, odeme ID, vb.)
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_history_order ON order_status_history (order_id);
CREATE INDEX idx_order_history_created ON order_status_history (created_at DESC);

-- -------------------------
-- ORDER NUMBER SEQUENCE
-- -------------------------
CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;
```

---

### V19 - Odeme Tablosu

```sql
-- =================================================================
-- V19__create_payment_table.sql
-- Odeme kayitlari (iyzico entegrasyonu)
-- =================================================================

CREATE TABLE payments (
    id                      BIGSERIAL PRIMARY KEY,
    order_id                BIGINT NOT NULL REFERENCES orders(id),
    payment_provider        VARCHAR(30) NOT NULL,       -- IYZICO, DOOR_PAYMENT
    provider_payment_id     VARCHAR(200),               -- iyzico paymentId
    conversation_id         VARCHAR(200),               -- iyzico conversationId (order ID ile eslestirme)
    basket_id               VARCHAR(200),               -- iyzico basketId

    -- Durum
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING -> PROCESSING -> SUCCESS / FAILED
    -- SUCCESS -> REFUND_PENDING -> REFUNDED / PARTIAL_REFUNDED

    -- Tutar
    amount                  NUMERIC(12, 2) NOT NULL,
    paid_amount             NUMERIC(12, 2),             -- Gercekte odenen (taksit farki olabilir)
    currency                VARCHAR(3) NOT NULL DEFAULT 'TRY',

    -- Taksit bilgisi
    installment_count       INTEGER NOT NULL DEFAULT 1,
    installment_price       NUMERIC(12, 2),             -- Taksitli toplam tutar

    -- Kart bilgisi (sadece son 4 hane ve tip)
    card_last_four          VARCHAR(4),
    card_type               VARCHAR(20),                -- VISA, MASTERCARD, TROY, AMEX
    card_association         VARCHAR(20),                -- CREDIT_CARD, DEBIT_CARD, PREPAID_CARD
    card_family             VARCHAR(50),                -- Bonus, Maximum, World, vb.
    card_bank_name          VARCHAR(100),

    -- 3D Secure
    three_d_secure          BOOLEAN NOT NULL DEFAULT FALSE,

    -- Hata bilgileri
    error_code              VARCHAR(50),
    error_message           VARCHAR(500),
    error_group             VARCHAR(50),

    -- Zaman damgalari
    paid_at                 TIMESTAMP,
    refunded_at             TIMESTAMP,
    refund_amount           NUMERIC(12, 2),

    -- iyzico ham yanit (debug icin)
    raw_request             JSONB,
    raw_response            JSONB,
    raw_callback            JSONB,

    -- IP ve guvenlik
    ip_address              VARCHAR(45),

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_payment_provider CHECK (payment_provider IN ('IYZICO', 'DOOR_PAYMENT')),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCESS', 'FAILED',
        'REFUND_PENDING', 'REFUNDED', 'PARTIAL_REFUNDED'
    )),
    CONSTRAINT chk_payment_currency CHECK (currency IN ('TRY', 'USD', 'EUR'))
);

CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_provider_id ON payments (provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payments_conversation ON payments (conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX idx_payments_created ON payments (created_at DESC);
```

---

### V20 - Favori, Yorum, Kupon, Bulten, CMS, Iade

```sql
-- =================================================================
-- V20__create_ecommerce_support_tables.sql
-- Yardimci e-ticaret tablolari
-- =================================================================

-- -------------------------
-- WISHLISTS (Favori urunler)
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
-- REVIEWS (Urun degerlendirmeleri)
-- -------------------------
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    order_id        BIGINT REFERENCES orders(id),       -- Dogrulanmis satin alma
    rating          SMALLINT NOT NULL,
    title           VARCHAR(200),
    comment         VARCHAR(2000),
    is_approved     BOOLEAN NOT NULL DEFAULT FALSE,      -- Admin moderasyonu
    admin_reply     VARCHAR(1000),                       -- Admin yaniti
    admin_reply_at  TIMESTAMP,
    is_visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    -- Her siparis icin bir urun bir kez degerlendirilir
    CONSTRAINT uq_review_per_order UNIQUE (customer_id, product_id, order_id)
);

CREATE INDEX idx_reviews_product ON reviews (product_id);
CREATE INDEX idx_reviews_customer ON reviews (customer_id);
CREATE INDEX idx_reviews_approved ON reviews (product_id, is_approved) WHERE is_approved = TRUE;
CREATE INDEX idx_reviews_rating ON reviews (product_id, rating);

-- -------------------------
-- COUPONS (Kupon/indirim sistemi)
-- -------------------------
CREATE TABLE coupons (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(50) NOT NULL,
    description             VARCHAR(500),
    discount_type           VARCHAR(20) NOT NULL,
    -- PERCENTAGE: Yuzde indirim
    -- FIXED_AMOUNT: Sabit tutar indirim
    -- FREE_SHIPPING: Ucretsiz kargo

    discount_value          NUMERIC(10, 2) NOT NULL,    -- Yuzde veya sabit tutar
    min_order_amount        NUMERIC(10, 2),             -- Minimum siparis tutari
    max_discount_amount     NUMERIC(10, 2),             -- Maksimum indirim tutari (yuzde icin)

    -- Kullanim limitleri
    usage_limit             INTEGER,                    -- Toplam kullanim limiti (NULL = sinirsiz)
    usage_count             INTEGER NOT NULL DEFAULT 0,
    per_customer_limit      INTEGER NOT NULL DEFAULT 1, -- Musteri basi kullanim

    -- Gecerlilik suresi
    valid_from              TIMESTAMP NOT NULL,
    valid_until             TIMESTAMP NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,

    -- Kapsam (NULL = tum urunler)
    applicable_category_ids BIGINT[],                   -- PostgreSQL array
    applicable_brand_ids    BIGINT[],
    applicable_product_ids  BIGINT[],
    excluded_product_ids    BIGINT[],                   -- Kapsam disi urunler

    -- Minimum urun adedi
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

-- Kupon kullanimlarinin takibi
CREATE TABLE coupon_usages (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT NOT NULL REFERENCES coupons(id),
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    discount_amount NUMERIC(10, 2) NOT NULL,            -- Bu kullanımda uygulanan indirim
    used_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coupon_usages_coupon ON coupon_usages (coupon_id);
CREATE INDEX idx_coupon_usages_customer ON coupon_usages (coupon_id, customer_id);

-- Carts tablosuna coupon FK ekle
ALTER TABLE carts ADD CONSTRAINT fk_carts_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL;

-- -------------------------
-- NEWSLETTER SUBSCRIPTIONS (Bulten aboneligi)
-- -------------------------
CREATE TABLE newsletter_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    customer_id     BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    unsubscribe_token VARCHAR(200),
    source          VARCHAR(50) DEFAULT 'WEBSITE',      -- WEBSITE, CHECKOUT, FOOTER
    subscribed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at TIMESTAMP,

    CONSTRAINT uq_newsletter_email UNIQUE (email)
);

CREATE INDEX idx_newsletter_active ON newsletter_subscriptions (is_active) WHERE is_active = TRUE;

-- -------------------------
-- CMS PAGES (Statik icerik sayfalari)
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
    -- CONTENT: Genel icerik
    -- LEGAL: Yasal sayfalar (KVKK, mesafeli satis, iade kosullari)
    -- FAQ: Sikca sorulan sorular

    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cms_slug UNIQUE (slug),
    CONSTRAINT chk_cms_type CHECK (page_type IN ('CONTENT', 'LEGAL', 'FAQ'))
);

CREATE INDEX idx_cms_slug ON cms_pages (slug);
CREATE INDEX idx_cms_published ON cms_pages (is_published, page_type) WHERE is_published = TRUE;

-- Varsayilan yasal sayfalari ekle
INSERT INTO cms_pages (slug, title, content, page_type, is_published, published_at) VALUES
    ('mesafeli-satis-sozlesmesi', 'Mesafeli Satis Sozlesmesi', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('gizlilik-ve-guvenlik', 'Gizlilik ve Guvenlik Politikasi', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('iptal-ve-iade-sartlari', 'Iptal ve Iade Sartlari', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('kvkk-aydinlatma-metni', 'KVKK Aydinlatma Metni', '<p>Icerik hazirlanacak</p>', 'LEGAL', TRUE, CURRENT_TIMESTAMP),
    ('hakkimizda', 'Hakkimizda', '<p>Icerik hazirlanacak</p>', 'CONTENT', TRUE, CURRENT_TIMESTAMP);

-- -------------------------
-- RETURN REQUESTS (Iade talepleri)
-- -------------------------
CREATE TABLE return_requests (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    customer_id         BIGINT NOT NULL REFERENCES customers(id),

    -- Durum
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING -> APPROVED -> CARGO_WAITING -> RECEIVED -> REFUND_PROCESSING -> REFUNDED
    -- PENDING -> REJECTED

    -- Iade nedeni
    reason              VARCHAR(50) NOT NULL,
    -- DEFECTIVE: Urun arizali/kusurlu
    -- WRONG_PRODUCT: Yanlis urun gonderildi
    -- NOT_AS_DESCRIBED: Urun tarifine uymuyor
    -- CHANGED_MIND: Cayma hakki (14 gun)
    -- DAMAGED_IN_SHIPPING: Kargoda hasarli
    -- OTHER: Diger

    description         VARCHAR(1000),
    cargo_tracking_no   VARCHAR(100),                   -- Iade kargo takip no
    cargo_company       VARCHAR(50),

    -- Iade tutari
    refund_amount       NUMERIC(12, 2),
    refund_method       VARCHAR(30),                    -- CREDIT_CARD, BANK_TRANSFER
    refunded_at         TIMESTAMP,

    -- Admin notlari
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

-- Iade kalem detaylari
CREATE TABLE return_request_items (
    id                  BIGSERIAL PRIMARY KEY,
    return_request_id   BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    order_item_id       BIGINT NOT NULL REFERENCES order_items(id),
    quantity            INTEGER NOT NULL,
    reason              VARCHAR(500),
    photo_urls          TEXT[],                          -- Urun fotolari (hasar kaniti)

    CONSTRAINT chk_return_item_qty CHECK (quantity >= 1)
);

CREATE INDEX idx_return_items_request ON return_request_items (return_request_id);
```

---

## 4. Mevcut Tablolarla Iliskiler Ozeti

### 4.1 Foreign Key Baglantilari

```
orders.customer_id          -> customers.id
orders.stock_transfer_id    -> stock_transfers.id  (MEVCUT TABLO)
orders.coupon_id            -> coupons.id

order_items.order_id        -> orders.id
order_items.product_id      -> products.id         (MEVCUT TABLO)
order_items.warehouse_id    -> warehouses.id        (MEVCUT TABLO)
order_items.stock_id        -> stocks.id            (MEVCUT TABLO)

cart_items.product_id       -> products.id          (MEVCUT TABLO)
wishlists.product_id        -> products.id          (MEVCUT TABLO)
reviews.product_id          -> products.id          (MEVCUT TABLO)
reviews.order_id            -> orders.id

payments.order_id           -> orders.id
return_requests.order_id    -> orders.id
customer_addresses.customer_id -> customers.id
carts.customer_id           -> customers.id
```

### 4.2 Mevcut Alanlarin Kullanimi

| Mevcut Alan | E-Ticaret Kullanimi |
|-------------|---------------------|
| `products.price` | Urun fiyati (KDV haric) |
| `products.vatRate` | KDV hesaplama (%1, %10, %20) |
| `products.sctRate` | OTV hesaplama |
| `products.shippingRate` | Kargo ucreti hesaplama |
| `products.lengthCm/widthCm/heightCm` | Desi hesaplama |
| `products.weight` | Kargo agirlik hesaplama |
| `stocks.quantity` | Stok miktari |
| `stocks.reservedQuantity` | Checkout sirasinda stok rezervasyonu |
| `stocks.getAvailableQuantity()` | Satin alinabilir miktar |
| `stock_transfers.transferType=CUSTOMER_DELIVERY` | Siparis karsilama |
| `stock_transfers.customerFullName/Phone/Address` | Teslimat bilgileri |
| `product_images.*` | Urun galeri gorselleri |
| `categories.parent/children` | Mega-menu hiyerarsisi |

### 4.3 Index Stratejisi

Yeni tablolar icin toplam 45+ index eklenir:
- **Primary key**: Her tablo icin otomatik
- **Foreign key**: Tum FK kolonlarinda
- **Arama**: slug, email, order_number, coupon_code
- **Filtreleme**: status, is_active, is_approved, created_at
- **Partial indexes**: WHERE kosulu ile sadece ilgili satirlari indexle (PostgreSQL ozeligi)

---

## 5. Veri Tipleri ve Turkiye Ozel Gereksinimleri

| Alan | Tip | Aciklama |
|------|-----|----------|
| `tc_kimlik_no` | VARCHAR(11) | 11 haneli Turk kimlik numarasi |
| `phone` | VARCHAR(20) | +90 5XX XXX XX XX formati |
| `city` | VARCHAR(100) | 81 il (Istanbul, Ankara, ...) |
| `district` | VARCHAR(100) | Ilce (Kadikoy, Cankaya, ...) |
| `tax_office` | VARCHAR(200) | Vergi dairesi adi |
| `tax_number` | VARCHAR(20) | 10 haneli vergi numarasi |
| `currency` | VARCHAR(3) | TRY (varsayilan), USD, EUR |
| `vat_rate` | NUMERIC(5,2) | %1, %10, %20 (Turkiye KDV oranlari) |
| `sct_rate` | NUMERIC(5,2) | Ozel Tuketim Vergisi |

---

## 6. Migrasyon Calistirma Sirasi

```
V15__add_ecommerce_fields_to_existing_tables.sql    (slug, SEO, indirim)
V16__create_customer_tables.sql                      (customers, addresses, refresh tokens)
V17__create_cart_tables.sql                          (carts, cart_items)
V18__create_order_tables.sql                         (orders, order_items, status_history)
V19__create_payment_table.sql                        (payments)
V20__create_ecommerce_support_tables.sql             (wishlists, reviews, coupons, CMS, returns)
```

Her migrasyon bagimsiz calisir ve sirayla uygulanir. V20'deki `ALTER TABLE carts ADD CONSTRAINT` V17'de olusturulan `carts` tablosuna bagli oldugu icin sira onemlidir.
