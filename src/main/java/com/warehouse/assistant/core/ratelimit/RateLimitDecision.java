package com.warehouse.assistant.core.ratelimit;

/**
 * Result of a rate limit check.
 *
 * @param allowed            true if the request should proceed
 * @param scope              which bucket was decisive (the first failing one, or the last checked if allowed)
 * @param retryAfterSeconds  non-zero only when {@code allowed == false}; indicates when the bucket refills
 * @param reachedGuestLimit  true when the decisive failure was the guest session cap — UI should show login CTA
 */
public record RateLimitDecision(
        boolean allowed,
        RateLimitScope scope,
        long retryAfterSeconds,
        boolean reachedGuestLimit
) {
    public static RateLimitDecision allowed(RateLimitScope scope) {
        return new RateLimitDecision(true, scope, 0L, false);
    }

    public static RateLimitDecision denied(RateLimitScope scope, long retryAfterSeconds, boolean reachedGuestLimit) {
        return new RateLimitDecision(false, scope, retryAfterSeconds, reachedGuestLimit);
    }
}
