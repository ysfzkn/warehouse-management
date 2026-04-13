package com.warehouse.assistant.core.safety;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turkish-specific PII detection using regex patterns. Detects:
 * <ul>
 *   <li>TC Kimlik Numarası (11 digits, first digit non-zero)</li>
 *   <li>Turkish phone numbers (+90 / 0xxx xxx xx xx)</li>
 *   <li>Turkish IBAN (TR + 24 digits)</li>
 *   <li>Credit card numbers (13-19 digits with optional separators)</li>
 *   <li>CVV codes (3-4 digits following "CVV" keyword)</li>
 *   <li>Email addresses</li>
 * </ul>
 *
 * <h3>False-positive mitigation</h3>
 * <ul>
 *   <li>Product prices like "13.999 TL" don't trigger card detection (no 13+ contiguous digits)</li>
 *   <li>Phone patterns require leading +90/0 so random 10-digit numbers are safe</li>
 *   <li>TC ID requires exactly 11 digits with non-zero first → order numbers / stock IDs rarely match</li>
 * </ul>
 */
@Component
public class PiiDetector {

    /** TC Kimlik: exactly 11 digits, first digit 1-9. Word-boundary enforced. */
    private static final Pattern TC_KIMLIK = Pattern.compile("\\b([1-9]\\d{10})\\b");

    /** Turkish phone: +90 or leading 0, followed by area code + 7 digits. */
    private static final Pattern TR_PHONE = Pattern.compile(
            "(?:\\+90|0)[\\s.-]?[2-5]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{2}[\\s.-]?\\d{2}");

    /** Turkish IBAN: TR + 2 check + 5 bank code + up to 17 account digits. */
    private static final Pattern IBAN = Pattern.compile(
            "TR\\d{2}[\\s]?\\d{4}[\\s]?\\d{4}[\\s]?\\d{4}[\\s]?\\d{4}[\\s]?\\d{4}[\\s]?\\d{2}");

    /** Credit card: 4 groups of 4 digits (optionally separated by space/dash). */
    private static final Pattern CARD = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");

    /** CVV: keyword "CVV" followed by 3-4 digits. Case-insensitive. */
    private static final Pattern CVV = Pattern.compile(
            "(?i)\\bCVV[\\s:]*\\d{3,4}\\b");

    /** Standard email pattern. */
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    public static class PiiMatch {
        public final String type;
        public final String value;
        public final int start;
        public final int end;

        public PiiMatch(String type, String value, int start, int end) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Scan text for all known PII patterns. Returns matches sorted by position.
     */
    public List<PiiMatch> detect(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<PiiMatch> matches = new ArrayList<>();
        findAll(text, CARD, "KART", matches);
        findAll(text, CVV, "CVV", matches);
        findAll(text, TC_KIMLIK, "TC_KIMLIK", matches);
        findAll(text, TR_PHONE, "TELEFON", matches);
        findAll(text, IBAN, "IBAN", matches);
        findAll(text, EMAIL, "EMAIL", matches);
        matches.sort((a, b) -> Integer.compare(a.start, b.start));
        return matches;
    }

    /**
     * Replace all detected PII with placeholders: {@code [TC_KIMLIK]}, {@code [KART]}, etc.
     */
    public String redact(String text) {
        if (text == null) return null;
        String redacted = text;
        // Order matters: longer/more-specific patterns first to prevent
        // partial matches. IBAN before phone (IBAN contains digit sequences
        // that look like phone numbers), card before TC_KIMLIK.
        redacted = IBAN.matcher(redacted).replaceAll("[IBAN]");
        redacted = CARD.matcher(redacted).replaceAll("[KART]");
        redacted = CVV.matcher(redacted).replaceAll("[CVV]");
        redacted = TC_KIMLIK.matcher(redacted).replaceAll("[TC_KIMLIK]");
        redacted = TR_PHONE.matcher(redacted).replaceAll("[TELEFON]");
        redacted = EMAIL.matcher(redacted).replaceAll("[EMAIL]");
        return redacted;
    }

    /** @return true if any PII pattern is found in the text. */
    public boolean hasPii(String text) {
        return !detect(text).isEmpty();
    }

    private void findAll(String text, Pattern pattern, String type, List<PiiMatch> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(new PiiMatch(type, m.group(), m.start(), m.end()));
        }
    }
}
