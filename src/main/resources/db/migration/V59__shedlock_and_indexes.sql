-- =============================================================================
-- V59 — ShedLock için lock table + production sıcak yollarda eksik indeksler.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) ShedLock — scheduled job'ların multi-instance'da tek instance'ta çalışması için
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

COMMENT ON TABLE shedlock IS
    'ShedLock multi-instance distributed lock table — scheduled job''lar burayı kilit olarak kullanır.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) Production sıcak sorgular için eksik indeksler
--    (EXPLAIN ANALYZE ile validate edilir lansman öncesi yük testinde)
-- ─────────────────────────────────────────────────────────────────────────────

-- Orders: müşteri siparişleri listesi + tarih sıralama (en sık sorgu)
CREATE INDEX IF NOT EXISTS idx_orders_customer_created
    ON orders(customer_id, created_at DESC);

-- Orders: status bazlı sorgular (admin dashboard, expiry job vb.)
CREATE INDEX IF NOT EXISTS idx_orders_status_created
    ON orders(status, created_at DESC);

-- Orders: kvkk_consent_at kontrolü (KVKK denetimi)
CREATE INDEX IF NOT EXISTS idx_orders_kvkk_consent_at
    ON orders(kvkk_consent_at)
    WHERE kvkk_consent_at IS NOT NULL;

-- Cart items: cart_id (sepet yükleme)
CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id
    ON cart_items(cart_id);

-- Stock: product_id + warehouse_id (stok sorguları)
CREATE INDEX IF NOT EXISTS idx_stocks_product_warehouse
    ON stocks(product_id, warehouse_id);

-- Invoice: order_id (sipariş detay sayfasında JOIN)
CREATE INDEX IF NOT EXISTS idx_invoices_order_id
    ON invoices(order_id);

-- Invoice: status + created_at (admin polling job)
CREATE INDEX IF NOT EXISTS idx_invoices_status_created
    ON invoices(status, created_at DESC);

-- Customer: anonimleştirilmiş hesapları filtrelemek için
CREATE INDEX IF NOT EXISTS idx_customers_anonymized
    ON customers(anonymized_at)
    WHERE anonymized_at IS NOT NULL;

-- Customer: email lookup (login flow — eğer yoksa)
CREATE INDEX IF NOT EXISTS idx_customers_email_lower
    ON customers(LOWER(email));

-- Product: slug lookup (PDP /urun/<slug>)
CREATE INDEX IF NOT EXISTS idx_products_slug
    ON products(slug)
    WHERE is_active = true;

-- Category: slug lookup
CREATE INDEX IF NOT EXISTS idx_categories_slug
    ON categories(slug)
    WHERE is_active = true;
