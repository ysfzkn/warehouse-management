package com.warehouse.assistant.core.safety;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a safety check (input validation, PII detection, jailbreak
 * detection, content moderation). Designed as a value object that aggregates
 * multiple findings so callers don't need to run checks one-by-one.
 */
public class SafetyCheckResult {

    public enum Action { PASS, WARN, BLOCK }

    private Action action = Action.PASS;
    private final List<String> findings = new ArrayList<>();
    private String sanitizedText;
    private boolean piiDetected;
    private boolean jailbreakDetected;
    private boolean inputTooLong;
    private boolean moderationFlagged;
    private String blockReason;

    public static SafetyCheckResult pass(String text) {
        SafetyCheckResult r = new SafetyCheckResult();
        r.sanitizedText = text;
        return r;
    }

    public void addFinding(String finding) { findings.add(finding); }
    public void markPiiDetected() { this.piiDetected = true; }
    public void markJailbreakDetected() { this.jailbreakDetected = true; }
    public void markInputTooLong() { this.inputTooLong = true; }
    public void markModerationFlagged() { this.moderationFlagged = true; }

    public void warn() { if (action != Action.BLOCK) action = Action.WARN; }
    public void block(String reason) { action = Action.BLOCK; blockReason = reason; }

    public Action getAction() { return action; }
    public List<String> getFindings() { return findings; }
    public String getSanitizedText() { return sanitizedText; }
    public void setSanitizedText(String t) { this.sanitizedText = t; }
    public boolean isPiiDetected() { return piiDetected; }
    public boolean isJailbreakDetected() { return jailbreakDetected; }
    public boolean isInputTooLong() { return inputTooLong; }
    public boolean isModerationFlagged() { return moderationFlagged; }
    public boolean isBlocked() { return action == Action.BLOCK; }
    public String getBlockReason() { return blockReason; }
}
