package com.warehouse.assistant.core.observability;

/**
 * Token usage + latency for a single chat turn, captured from the Spring AI
 * {@code ChatResponse.Metadata.Usage} object and handed to
 * {@link ConversationLogger} for persistence.
 */
public record UsageMetrics(
        int promptTokens,
        int completionTokens,
        int latencyMs
) {
    public static UsageMetrics zero() {
        return new UsageMetrics(0, 0, 0);
    }
}
