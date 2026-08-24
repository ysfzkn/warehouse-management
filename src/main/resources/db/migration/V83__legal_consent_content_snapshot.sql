ALTER TABLE orders ADD COLUMN IF NOT EXISTS legal_consent_snapshot JSONB;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_confirmation_ip VARCHAR(45);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_confirmation_user_agent VARCHAR(500);

COMMENT ON COLUMN orders.legal_consent_snapshot IS
    'Müşterinin onayladığı hukuki CMS metinlerinin tam içerik, SHA-256, sayfa id ve updated_at snapshotı.';
