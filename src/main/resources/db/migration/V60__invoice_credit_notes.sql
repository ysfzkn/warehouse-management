-- =============================================================================
-- V60 — İade faturası (Credit Note) desteği
-- =============================================================================
-- Türkiye e-fatura iade kuralları:
--   * e-Arşiv: 8 gün içinde iptal edilebilir. Sonrasında "iade faturası" kesilir.
--   * e-Fatura: hiçbir zaman iptal edilemez. Mutlaka credit note kesilmelidir.
--
-- Credit note kendisi yeni bir Invoice satırıdır; UBL-TR'de ProfileID="IADE"
-- ve BillingReference altında orijinal faturanın InvoiceDocumentReference
-- (numarası + uuid) yer alır. Orijinal Invoice satırı UNCHANGED kalır —
-- yasal denetim için kanıt korunur.
-- =============================================================================

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS is_credit_note BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS credited_invoice_id BIGINT REFERENCES invoices(id),
    ADD COLUMN IF NOT EXISTS credit_note_reason VARCHAR(500);

COMMENT ON COLUMN invoices.is_credit_note IS
    'Bu satır bir iade faturası ise true. Yeni Invoice satırıdır, orijinal değişmez.';
COMMENT ON COLUMN invoices.credited_invoice_id IS
    'Bu credit note, hangi orijinal faturayı iade ediyor (BillingReference için).';
COMMENT ON COLUMN invoices.credit_note_reason IS
    'İade nedeni (cayma hakkı, hasarlı ürün vb.). UBL-TR Note alanına yazılır.';

-- Sıkça sorgulanacak: bir orijinal faturanın credit note'larını listeleme
CREATE INDEX IF NOT EXISTS idx_invoices_credited_invoice
    ON invoices(credited_invoice_id)
    WHERE credited_invoice_id IS NOT NULL;

-- Sıkça sorgulanacak: tüm credit note'ları filtreleme (admin reporting)
CREATE INDEX IF NOT EXISTS idx_invoices_is_credit_note
    ON invoices(is_credit_note)
    WHERE is_credit_note = TRUE;
