package com.warehouse.assistant.core.safety;

import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates all safety checks for the assistant platform. Both
 * {@code WmsAssistantChatService} and {@code StoreAssistantChatService}
 * call this pipeline before and after the LLM invocation:
 *
 * <pre>
 *   SafetyCheckResult inputCheck = pipeline.validateInput(profile, userId, userMessage);
 *   if (inputCheck.isBlocked()) return friendlyBlockedResponse();
 *   String sanitizedInput = inputCheck.getSanitizedText();
 *
 *   // ... call LLM with sanitizedInput ...
 *
 *   String sanitizedOutput = pipeline.validateOutput(profile, userId, llmResponse);
 * </pre>
 *
 * The pipeline is intentionally non-blocking for most cases — PII is
 * redacted (not blocked), jailbreaks are logged (not blocked, prompt defense
 * handles them), only empty/null input triggers a hard block. This keeps the
 * UX smooth while maintaining defense-in-depth.
 */
@Service
public class ContentSafetyPipeline {

    private final InputValidator inputValidator;
    private final PiiDetector piiDetector;
    private final JailbreakDetector jailbreakDetector;
    private final SecurityAuditLogger auditLogger;
    private final AssistantRuntimeConfig config;

    public ContentSafetyPipeline(InputValidator inputValidator,
                                  PiiDetector piiDetector,
                                  JailbreakDetector jailbreakDetector,
                                  SecurityAuditLogger auditLogger,
                                  AssistantRuntimeConfig config) {
        this.inputValidator = inputValidator;
        this.piiDetector = piiDetector;
        this.jailbreakDetector = jailbreakDetector;
        this.auditLogger = auditLogger;
        this.config = config;
    }

    /**
     * Pre-LLM input validation. Returns a result with:
     * <ul>
     *   <li>{@code sanitizedText} — the message to send to the LLM (PII redacted if configured)</li>
     *   <li>{@code isBlocked()} — true only if the message should not be sent at all</li>
     *   <li>{@code findings} — audit trail of what was detected</li>
     * </ul>
     */
    public SafetyCheckResult validateInput(AssistantProfile profile, String userId, String userMessage) {
        SafetyCheckResult result = SafetyCheckResult.pass(userMessage);

        // 1. Input length + null check
        inputValidator.validate(userMessage, result);
        if (result.isBlocked()) return result;

        String text = result.getSanitizedText();

        // 2. PII detection (runtime-configurable via admin UI)
        if (config.isInputPiiDetection()) {
            List<PiiDetector.PiiMatch> matches = piiDetector.detect(text);
            if (!matches.isEmpty()) {
                result.markPiiDetected();
                for (PiiDetector.PiiMatch m : matches) {
                    result.addFinding("PII:" + m.type + " at " + m.start);
                }
                auditLogger.logPiiDetected(profile, userId, matches.size());

                String action = config.getInputPiiAction();
                switch (action.toUpperCase()) {
                    case "BLOCK" -> {
                        result.block("Mesajınızda kişisel veri tespit edildi. Lütfen kart numarası, TC kimlik veya IBAN gibi bilgileri paylaşmayın.");
                        return result;
                    }
                    case "REDACT" -> {
                        result.setSanitizedText(piiDetector.redact(text));
                        result.warn();
                    }
                    default -> result.warn();
                }
            }
        }

        // 3. Jailbreak detection (runtime-configurable)
        if (config.isInputJailbreakDetection()) {
            String pattern = jailbreakDetector.detectPattern(result.getSanitizedText());
            if (pattern != null) {
                result.markJailbreakDetected();
                result.addFinding("JAILBREAK_PATTERN:" + pattern);
                auditLogger.logJailbreakAttempt(profile, userId, pattern);
                // DO NOT block — prompt-level defense handles it. Just audit log.
                result.warn();
            }
        }

        return result;
    }

    /**
     * Post-LLM output validation. Runs PII redaction on the LLM response
     * as a defense-in-depth measure (the LLM shouldn't output PII, but if
     * it does, we catch it here before it reaches the user/logs).
     */
    public String validateOutput(AssistantProfile profile, String userId, String llmResponse) {
        if (llmResponse == null) return "";

        String sanitized = llmResponse;

        // 1. Hard character limit (workaround for GPT-5.1 not supporting max_tokens
        // via Spring AI M6). Admin-configurable via "maxResponseTokens" setting.
        // 1 token ≈ 3-4 TR characters, so 1500 tokens ≈ 5000 chars.
        int maxChars = config.getMaxResponseTokens() * 4;
        if (sanitized.length() > maxChars) {
            // Truncate at the last sentence boundary before the limit
            int cutPoint = findLastSentenceBoundary(sanitized, maxChars);
            sanitized = sanitized.substring(0, cutPoint).trim();
            if (!sanitized.endsWith(".") && !sanitized.endsWith("?") && !sanitized.endsWith("!")) {
                sanitized += "…";
            }
        }

        // 2. Output PII redaction (runtime-configurable)
        if (config.isOutputPiiRedaction()) {
            if (piiDetector.hasPii(sanitized)) {
                auditLogger.logPiiDetected(profile, userId + ":output", piiDetector.detect(sanitized).size());
                sanitized = piiDetector.redact(sanitized);
            }
        }

        return sanitized;
    }

    /**
     * Find the last sentence-ending character (. ! ? or newline) before the
     * given position, so we don't cut mid-word or mid-sentence.
     */
    private int findLastSentenceBoundary(String text, int maxPos) {
        int pos = Math.min(maxPos, text.length());
        for (int i = pos - 1; i > Math.max(0, pos - 500); i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }
        // No sentence boundary found in last 500 chars — hard cut at word boundary
        int spacePos = text.lastIndexOf(' ', pos);
        return spacePos > 0 ? spacePos : pos;
    }
}
