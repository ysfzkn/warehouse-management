-- Invoice number generator state table.
-- One row per (series, year) tuple; last_seq is atomically bumped via
-- "INSERT ... ON CONFLICT DO UPDATE ... RETURNING last_seq" so concurrent
-- invoice creations never collide on the same number.
--
-- Format produced by InvoiceNumberGenerator:
--   {series}{yyyy}{9-digit zero-padded seq}
-- e.g. EAR2026000001, EFA2026000017
--
-- Series convention:
--   EAR = e-Arşiv (bireysel)
--   EFA = e-Fatura (tüzel)
--
-- GİB allows the seller to assign a 16-char invoice number (3 letters + 4 digit
-- year + 9 digit sequence). Once a series+year is in use, the sequence must be
-- strictly monotonic and gap-free is preferred.

CREATE TABLE IF NOT EXISTS invoice_number_sequence (
    series    VARCHAR(3)  NOT NULL,
    year      INTEGER     NOT NULL,
    last_seq  BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMP  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (series, year)
);
