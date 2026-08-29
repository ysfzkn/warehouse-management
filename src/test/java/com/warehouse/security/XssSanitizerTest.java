package com.warehouse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The previous sanitiser was a regex denylist whose event-handler pattern required
 * quotes, so the most common XSS payloads walked straight through it. These cases pin
 * the allowlist behaviour that replaced it.
 */
class XssSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Unquoted handler — the exact shape the old regex could not match.
            "<img src=x onerror=alert(1)>",
            "<svg onload=alert(1)>",
            "<svg/onload=alert(1)>",
            "<body onload=alert(1)>",
            "<iframe src=javascript:alert(1)></iframe>",
            "<a href=\"javascript:alert(1)\">click</a>",
            "<script>alert(1)</script>",
            "<scr<script>ipt>alert(1)</script>",
            "<object data=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\"></object>",
            "<embed src=\"x.svg\">",
            "<form action=\"https://evil.example\"><input name=q></form>",
            "<math><mtext><style><img src=x onerror=alert(1)>",
            "<input onfocus=alert(1) autofocus>",
            "<details open ontoggle=alert(1)>",
    })
    void stripsExecutableContent(String payload) {
        String cleaned = XssSanitizer.sanitize(payload);
        String lower = cleaned.toLowerCase();
        assertThat(lower).doesNotContain("<script");
        assertThat(lower).doesNotContain("javascript:");
        assertThat(lower).doesNotContain("<object");
        assertThat(lower).doesNotContain("<embed");
        assertThat(lower).doesNotContain("<form");
        // No attribute starting with "on" may survive.
        assertThat(lower).doesNotContainPattern("\\son[a-z]+\\s*=");
    }

    @Test
    void keepsTheFormattingTheRichTextEditorProduces() {
        String html = "<p class=\"ql-align-center\"><strong>Kampanya</strong> "
                + "<span style=\"color: rgb(230, 0, 0);\">yuzde 20</span></p>"
                + "<ul><li>Ucretsiz kargo</li></ul>"
                + "<a href=\"https://example.com/kampanya\" target=\"_blank\">Detay</a>";
        String cleaned = XssSanitizer.sanitizeRichText(html);
        assertThat(cleaned).contains("<strong>Kampanya</strong>");
        assertThat(cleaned).contains("ql-align-center");
        assertThat(cleaned).contains("<li>Ucretsiz kargo</li>");
        assertThat(cleaned).contains("https://example.com/kampanya");
        // target=_blank without rel=noopener lets the opened page control this one.
        assertThat(cleaned).contains("noopener");
    }

    @Test
    void keepsAllowlistedEmbeds() {
        String html = "<iframe src=\"https://www.google.com/maps/embed?pb=1\" width=\"600\" "
                + "height=\"450\" allowfullscreen></iframe>";
        String cleaned = XssSanitizer.sanitizeRichText(html);
        assertThat(cleaned).contains("<iframe");
        assertThat(cleaned).contains("https://www.google.com/maps/embed");
    }

    /**
     * The global deserialiser runs on every inbound string, so it must not corrupt
     * ordinary text. The old implementation also trimmed everything it touched, which
     * silently rewrote passwords that began or ended with a space.
     */
    @Test
    void leavesOrdinaryTextByteForByte() {
        for (String value : new String[]{
                "5 < 6 && 7 > 3",
                "Fiyat: 1.299,90 TL (KDV dahil)",
                "  P@ssw0rd with spaces  ",
                "<john.doe@example.com>",
                "a<3b",
                "SELECT * FROM products WHERE id = 1",
        }) {
            assertThat(XssSanitizer.sanitize(value))
                    .as("plain text must be preserved exactly: %s", value)
                    .isEqualTo(value);
        }
    }

    @Test
    void stripsControlCharactersButKeepsWhitespace() {
        assertThat(XssSanitizer.sanitize("abc" + (char) 0 + "def")).isEqualTo("abcdef");
        assertThat(XssSanitizer.sanitize("line one\nline two\ttabbed"))
                .isEqualTo("line one\nline two\ttabbed");
    }

    @Test
    void handlesNull() {
        assertThat(XssSanitizer.sanitize(null)).isNull();
        assertThat(XssSanitizer.sanitizeRichText(null)).isNull();
    }
}
