package com.warehouse.assistant.core.api;

/**
 * Identifies which assistant profile a request belongs to.
 * <p>
 * A profile bundles together: a system prompt, a tool set, a role policy,
 * a security chain and an observability bucket. Every request that flows
 * through the core orchestration layer carries exactly one profile so that
 * the rest of the pipeline (rate limiting, cost attribution, logging,
 * conversation persistence) can branch cleanly.
 */
public enum AssistantProfile {
    /** Internal warehouse operators (ADMIN / STOCK_IN / STOCK_OUT). */
    WMS,

    /** Public storefront — guests and authenticated customers. */
    STORE
}
