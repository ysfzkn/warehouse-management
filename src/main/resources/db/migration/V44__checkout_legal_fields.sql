-- V44: Add preliminary information form consent tracking to orders.
-- Turkish e-commerce law requires both "Ön Bilgilendirme Formu" and
-- "Mesafeli Satış Sözleşmesi" to be explicitly accepted before purchase.
ALTER TABLE orders ADD COLUMN preliminary_info_accepted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN preliminary_info_accepted_at TIMESTAMP NULL;

-- Also add KVKK consent tracking to contact messages
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS kvkk_consent BOOLEAN NOT NULL DEFAULT FALSE;
