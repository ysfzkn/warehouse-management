package com.warehouse.service.invoice;

import com.warehouse.enums.InvoiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Generates GİB-compliant invoice numbers.
 *
 * <p>Format: {@code {3-char series}{yyyy}{9-digit zero-padded seq}}
 * <br>Examples: {@code EAR2026000001}, {@code EFA2026000017}
 *
 * <p>The sequence state lives in the {@code invoice_number_sequence} table —
 * one row per (series, year). The bump is done atomically via
 * PostgreSQL's {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING}
 * so concurrent invoice creations cannot collide on the same number.
 *
 * <p>The generator runs in a {@link Propagation#REQUIRES_NEW} transaction
 * so the number is committed even if the surrounding invoice transaction
 * later rolls back. This trades a tiny risk of gaps (acceptable) for the
 * much larger benefit of no duplicate numbers ever.
 */
@Service
public class InvoiceNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberGenerator.class);

    private final JdbcTemplate jdbc;

    public InvoiceNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Next number for the given invoice type, using the current year.
     *
     * @return e.g. {@code "EAR2026000042"}
     */
    public String next(InvoiceType type) {
        return next(seriesFor(type), Year.now().getValue());
    }

    /** Next number for explicit series + year (exposed for tests / manual issuance). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String series, int year) {
        if (series == null || series.length() != 3) {
            throw new IllegalArgumentException("Series must be exactly 3 characters");
        }
        String upperSeries = series.toUpperCase();

        // Atomic upsert + increment — returns the new sequence value.
        Long nextSeq = jdbc.queryForObject("""
                INSERT INTO invoice_number_sequence (series, year, last_seq, updated_at)
                VALUES (?, ?, 1, NOW())
                ON CONFLICT (series, year) DO UPDATE
                  SET last_seq   = invoice_number_sequence.last_seq + 1,
                      updated_at = NOW()
                RETURNING last_seq
                """, Long.class, upperSeries, year);

        if (nextSeq == null) {
            throw new IllegalStateException("Invoice number generation failed (null seq)");
        }

        String number = String.format("%s%04d%09d", upperSeries, year, nextSeq);
        log.debug("Generated invoice number: {}", number);
        return number;
    }

    /** Map invoice type → 3-letter series code. */
    public static String seriesFor(InvoiceType type) {
        if (type == null) return "EAR";
        return switch (type) {
            case E_FATURA -> "EFA";
            case E_ARSIV  -> "EAR";
        };
    }
}
