package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Denylist for JWTs that must stop working before their natural expiry.
 *
 * <p>Two granularities are supported:
 * <ul>
 *   <li><b>Per token</b> — {@link #revokeToken(String, Instant)} on logout, keyed by
 *       the token's {@code jti}.</li>
 *   <li><b>Per subject</b> — {@link #revokeAllForSubject(String)} after a password
 *       reset or an admin-initiated password change. Every token issued <em>before</em>
 *       the cut-off instant is rejected, which is the only way to invalidate tokens
 *       whose {@code jti} we never recorded.</li>
 * </ul>
 *
 * <p>Backed by Caffeine, so entries are dropped once the underlying token would have
 * expired anyway — the denylist can never grow beyond the token lifetime. This is
 * in-memory: on a multi-instance deployment each instance keeps its own copy and a
 * restart clears it. Both are acceptable because the fallback is the token's own
 * (short) expiry, but moving this to Redis is the natural next step when the app is
 * scaled horizontally.
 */
@Service
public class TokenRevocationService {

    /** Longest lifetime any token in this system can have (customer refresh window). */
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofDays(31);

    private final Cache<String, Boolean> revokedJtis = Caffeine.newBuilder()
            .expireAfterWrite(MAX_TOKEN_LIFETIME)
            .maximumSize(200_000)
            .build();

    private final Cache<String, Instant> subjectCutoffs = Caffeine.newBuilder()
            .expireAfterWrite(MAX_TOKEN_LIFETIME)
            .maximumSize(100_000)
            .build();

    /** Revoke a single token by its {@code jti}. Safe to call with null. */
    public void revokeToken(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return; // already dead
        revokedJtis.put(jti, Boolean.TRUE);
    }

    /**
     * Invalidate every token issued for {@code subject} up to now. Used after a
     * password reset so a stolen session cannot outlive the credential it was
     * created with.
     */
    public void revokeAllForSubject(String subject) {
        if (subject == null || subject.isBlank()) return;
        // One second into the future: tokens carry second-precision "issued at"
        // claims, so a token minted in this same second must also be caught.
        subjectCutoffs.put(subject.toLowerCase(), Instant.now().plusSeconds(1));
    }

    /** True when the token must be rejected. */
    public boolean isRevoked(String jti, String subject, Instant issuedAt) {
        if (jti != null && !jti.isBlank() && Boolean.TRUE.equals(revokedJtis.getIfPresent(jti))) {
            return true;
        }
        if (subject == null || issuedAt == null) return false;
        Instant cutoff = subjectCutoffs.getIfPresent(subject.toLowerCase());
        return cutoff != null && issuedAt.isBefore(cutoff);
    }
}
