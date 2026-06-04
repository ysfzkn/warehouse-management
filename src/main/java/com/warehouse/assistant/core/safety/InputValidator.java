package com.warehouse.assistant.core.safety;

import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import org.springframework.stereotype.Component;

/**
 * Validates raw user input before it enters the LLM pipeline.
 * <ul>
 *   <li>Length cap: prevents token abuse and DoS via oversized messages.</li>
 *   <li>Null/empty check: returns a friendly fallback instead of a 500.</li>
 * </ul>
 */
@Component
public class InputValidator {

    private final AssistantRuntimeConfig config;

    public InputValidator(AssistantRuntimeConfig config) {
        this.config = config;
    }

    /**
     * Validate and sanitize a single user message.
     * Mutates the given {@link SafetyCheckResult} in place.
     */
    public void validate(String userMessage, SafetyCheckResult result) {
        if (userMessage == null || userMessage.isBlank()) {
            result.setSanitizedText("");
            result.block("Boş mesaj gönderilemez.");
            return;
        }

        int maxLen = config.getInputMaxLength();
        if (userMessage.length() > maxLen) {
            result.markInputTooLong();
            result.addFinding("INPUT_TOO_LONG: " + userMessage.length() + " > " + maxLen);
            // Truncate rather than block — the user may have pasted a long text accidentally.
            result.setSanitizedText(userMessage.substring(0, maxLen));
            result.warn();
        } else {
            result.setSanitizedText(userMessage);
        }
    }
}
