package com.warehouse.assistant.core.safety;

import org.springframework.stereotype.Component;

/**
 * Redacts PII from text before it is persisted to conversation logs.
 * Thin wrapper over {@link PiiDetector#redact(String)} — exists as a
 * separate bean so it can be injected into {@code ConversationLogger}
 * independently and swapped for a no-op in tests.
 */
@Component
public class LogSanitizer {

    private final PiiDetector piiDetector;

    public LogSanitizer(PiiDetector piiDetector) {
        this.piiDetector = piiDetector;
    }

    /**
     * Redact all known PII patterns. Safe to call on null (returns null).
     */
    public String sanitize(String text) {
        if (text == null) return null;
        return piiDetector.redact(text);
    }
}
