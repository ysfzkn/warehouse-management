package com.warehouse.assistant.core.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PiiDetectorTest {

    private PiiDetector detector;

    @BeforeEach
    void setUp() { detector = new PiiDetector(); }

    // ── TC Kimlik ──
    @Test void detectsTcKimlik() { assertTrue(detector.hasPii("Numaram 12345678901")); }
    @Test void tcKimlikRedacted() { assertEquals("Numaram [TC_KIMLIK]", detector.redact("Numaram 12345678901")); }
    @Test void tcKimlikIgnoresLeadingZero() { assertFalse(detector.hasPii("Kod: 01234567890")); }

    // ── Phone ──
    @Test void detectsTrPhone() { assertTrue(detector.hasPii("+90 532 123 45 67")); }
    @Test void detectsTrPhoneZeroPrefix() { assertTrue(detector.hasPii("0532 123 45 67")); }
    @Test void phoneRedacted() { assertEquals("Ara: [TELEFON]", detector.redact("Ara: 0532 123 45 67")); }

    // ── IBAN ──
    @Test void detectsIban() { assertTrue(detector.hasPii("TR330006100519786457841326")); }
    @Test void ibanRedacted() {
        String r = detector.redact("IBAN: TR330006100519786457841326");
        assertTrue(r.contains("[IBAN]"), "Expected IBAN redacted, got: " + r);
    }

    // ── Credit Card ──
    @Test void detectsCreditCard() { assertTrue(detector.hasPii("4111 1111 1111 1111")); }
    @Test void detectsCreditCardNoDash() { assertTrue(detector.hasPii("4111111111111111")); }
    @Test void cardRedacted() { assertEquals("Kart: [KART]", detector.redact("Kart: 4111 1111 1111 1111")); }

    // ── CVV ──
    @Test void detectsCvv() { assertTrue(detector.hasPii("CVV: 123")); }
    @Test void cvvRedacted() { assertTrue(detector.redact("CVV:456").contains("[CVV]")); }

    // ── Email ──
    @Test void detectsEmail() { assertTrue(detector.hasPii("Mail: test@example.com")); }
    @Test void emailRedacted() { assertEquals("Mail: [EMAIL]", detector.redact("Mail: test@example.com")); }

    // ── False Positives ──
    @Test void priceNotCard() {
        // "13.999 TL" should NOT trigger card detection (dots, not 16 digits)
        assertFalse(detector.hasPii("Fiyat: 13.999 TL"));
    }
    @Test void shortNumberNotTc() {
        // 5-digit stock ID should not trigger TC
        assertFalse(detector.hasPii("Stok ID: 12345"));
    }
    @Test void orderNumberNotPii() {
        assertFalse(detector.hasPii("Sipariş no: SIP-20250101-001"));
    }

    // ── Multiple PII ──
    @Test void multiplePiiDetected() {
        String text = "TC: 12345678901, Kart: 4111 1111 1111 1111, Mail: a@b.com";
        List<PiiDetector.PiiMatch> matches = detector.detect(text);
        assertTrue(matches.size() >= 3, "Expected ≥3 PII matches, got " + matches.size());
    }
    @Test void multiplePiiRedacted() {
        String text = "TC: 12345678901, Kart: 4111 1111 1111 1111";
        String r = detector.redact(text);
        assertTrue(r.contains("[TC_KIMLIK]"));
        assertTrue(r.contains("[KART]"));
        assertFalse(r.contains("12345678901"));
        assertFalse(r.contains("4111"));
    }

    // ── Null / empty ──
    @Test void nullSafe() { assertFalse(detector.hasPii(null)); }
    @Test void emptySafe() { assertFalse(detector.hasPii("")); }
    @Test void redactNullReturnsNull() { assertNull(detector.redact(null)); }
}
