package com.warehouse.assistant.core.safety;

import com.warehouse.assistant.core.api.AssistantProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dedicated logger for security-relevant events in the assistant pipeline.
 * Writes to a separate SLF4J logger ({@code assistant.security}) so ops can
 * route these to a separate log file / alert sink without touching app logs.
 * <p>
 * Events logged:
 * <ul>
 *   <li>PII detected in user input</li>
 *   <li>Jailbreak attempt detected</li>
 *   <li>Content moderation flag raised</li>
 *   <li>Input too long (truncated)</li>
 *   <li>Possible hallucination detected</li>
 * </ul>
 */
@Component
public class SecurityAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("assistant.security");

    public void logPiiDetected(AssistantProfile profile, String userId, int piiCount) {
        audit.warn("PII_DETECTED profile={} user={} count={}", profile, mask(userId), piiCount);
    }

    public void logJailbreakAttempt(AssistantProfile profile, String userId, String pattern) {
        audit.warn("JAILBREAK_ATTEMPT profile={} user={} pattern={}", profile, mask(userId), pattern);
    }

    public void logContentModeration(AssistantProfile profile, String userId, String category, int severity) {
        audit.warn("CONTENT_MODERATION profile={} user={} category={} severity={}", profile, mask(userId), category, severity);
    }

    public void logInputTooLong(AssistantProfile profile, String userId, int length, int maxLength) {
        audit.info("INPUT_TOO_LONG profile={} user={} length={} max={}", profile, mask(userId), length, maxLength);
    }

    public void logPossibleHallucination(AssistantProfile profile, String userId, String detail) {
        audit.warn("POSSIBLE_HALLUCINATION profile={} user={} detail={}", profile, mask(userId), truncate(detail, 200));
    }

    private String mask(String userId) {
        if (userId == null || userId.length() <= 4) return "***";
        return userId.substring(0, 2) + "***" + userId.substring(userId.length() - 2);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
