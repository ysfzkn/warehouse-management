package com.warehouse.assistant.core.api;

/**
 * Marker interface for assistant chat orchestration services.
 * <p>
 * Each concrete profile (WMS, STORE) provides its own implementation that
 * encapsulates prompt building, tool selection, rate limiting and LLM
 * invocation. Keeping the interface empty in Phase 0 lets us refactor the
 * existing {@code CezeriChatService} without touching its method signatures.
 * Phase 1 will evolve this into a richer contract (see the plan at
 * {@code C:/Users/asus/.claude/plans/iterative-wiggling-hare.md}).
 */
public interface AssistantChatService {

    /**
     * @return which profile this service implements.
     */
    AssistantProfile profile();
}
