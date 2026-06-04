package com.warehouse.assistant.core.ratelimit;

import com.warehouse.assistant.core.config.AssistantProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Bucket4j-backed rate limiter for the assistant platform.
 * <p>
 * Buckets are keyed by {@link RateLimitScope} + actor key (guest session id,
 * customer id, WMS user id, IP). We deliberately keep everything in-process:
 * <ul>
 *   <li>Railway single-instance deploy → no distribution headaches.</li>
 *   <li>Worst case on restart we reset counters — acceptable for chat.</li>
 *   <li>Upgrading to distributed (Redis) is a one-class swap later.</li>
 * </ul>
 * <p>
 * The limits are read from {@link AssistantProperties} so the admin can
 * loosen/tighten without a code change.
 */
@Service
public class AssistantRateLimiter {

    private final AssistantProperties props;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AssistantRateLimiter(AssistantProperties props) {
        this.props = props;
    }

    // -------------------------------------------------------------------------
    // Public API — one call per scope
    // -------------------------------------------------------------------------

    /**
     * Guest session cap. After reaching the cap the controller should surface
     * the "please log in" prompt rather than a generic 429, so we mark the
     * decision with {@code reachedGuestLimit=true}.
     */
    public RateLimitDecision checkGuestSession(String guestSessionId) {
        int max = props.getRatelimit().getGuestSessionMax();
        Bucket b = bucketFor("guest-sess:" + guestSessionId, bandwidth(max, Duration.ofHours(24)));
        return tryConsume(b, RateLimitScope.GUEST_SESSION, true);
    }

    /** Per-IP guest cap over a rolling 24h window. */
    public RateLimitDecision checkGuestIp(String ipHash) {
        int max = props.getRatelimit().getGuestIpDaily();
        Bucket b = bucketFor("guest-ip:" + ipHash, bandwidth(max, Duration.ofHours(24)));
        return tryConsume(b, RateLimitScope.GUEST_IP, false);
    }

    /** Customer hourly cap. */
    public RateLimitDecision checkCustomerHourly(long customerId) {
        int max = props.getRatelimit().getCustomerHourly();
        Bucket b = bucketFor("cust-h:" + customerId, bandwidth(max, Duration.ofHours(1)));
        return tryConsume(b, RateLimitScope.CUSTOMER_HOURLY, false);
    }

    /** Customer daily cap. */
    public RateLimitDecision checkCustomerDaily(long customerId) {
        int max = props.getRatelimit().getCustomerDaily();
        Bucket b = bucketFor("cust-d:" + customerId, bandwidth(max, Duration.ofHours(24)));
        return tryConsume(b, RateLimitScope.CUSTOMER_DAILY, false);
    }

    /** WMS user hourly cap. */
    public RateLimitDecision checkWmsHourly(String username) {
        int max = props.getRatelimit().getWmsHourly();
        Bucket b = bucketFor("wms-h:" + username, bandwidth(max, Duration.ofHours(1)));
        return tryConsume(b, RateLimitScope.WMS_HOURLY, false);
    }

    /** Bucket4j 8.14+ fluent bandwidth builder. Refill == capacity every {@code period}. */
    private static Bandwidth bandwidth(int capacity, Duration period) {
        return Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, period)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Bucket bucketFor(String key, Bandwidth bandwidth) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(bandwidth).build());
    }

    private RateLimitDecision tryConsume(Bucket bucket, RateLimitScope scope, boolean isGuestSessionCap) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allowed(scope);
        }
        long retryAfter = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        return RateLimitDecision.denied(scope, retryAfter, isGuestSessionCap);
    }
}
