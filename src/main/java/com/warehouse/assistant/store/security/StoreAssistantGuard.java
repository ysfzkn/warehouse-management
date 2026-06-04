package com.warehouse.assistant.store.security;

import com.warehouse.assistant.core.observability.ConversationLogger;
import com.warehouse.assistant.core.ratelimit.AssistantRateLimiter;
import com.warehouse.assistant.core.ratelimit.RateLimitDecision;
import com.warehouse.assistant.core.ratelimit.RateLimitScope;
import com.warehouse.security.JwtService;
import com.warehouse.util.CustomerTokenExtractor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves caller identity and runs all rate-limit checks for the storefront
 * assistant endpoint. Keeping the logic here means the controller stays thin
 * and the chat service never has to care about cookies or Bucket4j.
 *
 * <p>Cookie contract:
 * <ul>
 *   <li>{@code assistant_guest_sid} — HttpOnly, 24h TTL, SameSite=Lax</li>
 *   <li>Issued on the first guest request; persists across page loads</li>
 *   <li>Cleared when the customer logs in? — no, we leave it alone; customer
 *       id takes precedence over the guest session everywhere</li>
 * </ul>
 */
@Component
public class StoreAssistantGuard {

    private static final String GUEST_COOKIE = "assistant_guest_sid";
    private static final int GUEST_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

    private final JwtService jwtService;
    private final AssistantRateLimiter rateLimiter;

    public StoreAssistantGuard(JwtService jwtService, AssistantRateLimiter rateLimiter) {
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    public StoreAssistantActor resolveActor(HttpServletRequest request, HttpServletResponse response) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String guestId = ensureGuestSessionCookie(request, response);
        String ipHash = ConversationLogger.hashIp(clientIp(request));
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 512) {
            userAgent = userAgent.substring(0, 512);
        }
        return new StoreAssistantActor(customerId, guestId, ipHash, userAgent);
    }

    private String ensureGuestSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (GUEST_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        String fresh = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(GUEST_COOKIE, fresh);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(GUEST_COOKIE_MAX_AGE_SECONDS);
        cookie.setPath("/");
        // SameSite=Lax is the default on modern Tomcat; explicitly set for safety
        response.addHeader("Set-Cookie",
                "%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax".formatted(
                        GUEST_COOKIE, fresh, GUEST_COOKIE_MAX_AGE_SECONDS));
        return fresh;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }

    // -------------------------------------------------------------------------
    // Rate limit pipeline
    // -------------------------------------------------------------------------

    /**
     * Run the rate limit ladder for the actor. Returns the first failing
     * decision, or an allowed decision keyed by the last bucket we checked.
     */
    public RateLimitDecision checkRateLimit(StoreAssistantActor actor) {
        if (actor.isGuest()) {
            RateLimitDecision session = rateLimiter.checkGuestSession(actor.guestSessionId());
            if (!session.allowed()) return session;
            RateLimitDecision ip = rateLimiter.checkGuestIp(actor.ipHash() != null ? actor.ipHash() : "unknown");
            if (!ip.allowed()) return ip;
            return session; // allowed: report the most user-facing bucket
        }
        RateLimitDecision hour = rateLimiter.checkCustomerHourly(actor.customerId());
        if (!hour.allowed()) return hour;
        RateLimitDecision day = rateLimiter.checkCustomerDaily(actor.customerId());
        if (!day.allowed()) return day;
        return hour;
    }

    /**
     * Map a deny decision to a Turkish friendly message suitable for direct
     * display in the chat bubble.
     */
    public String friendlyDenyMessage(RateLimitDecision decision) {
        if (decision.reachedGuestLimit()) {
            return """
                    Şu an misafir olarak sohbet ediyorsunuz ve hızlı deneme hakkınız doldu.
                    Daha fazla yardımcı olabilmem için **üye girişi yapmanızı rica ediyorum**;
                    hesabınız yoksa hızlıca **ücretsiz üye olabilirsiniz**.
                    """;
        }
        if (decision.scope() == RateLimitScope.GUEST_IP) {
            return "Günlük ücretsiz kullanım hakkınız doldu. Lütfen üye girişi yapın veya yarın tekrar deneyin.";
        }
        long retry = Math.max(1, decision.retryAfterSeconds());
        String period = retry >= 60 ? (retry / 60) + " dakika" : retry + " saniye";
        return "Kısa süreli bir kullanım sınırı devreye girdi. Lütfen yaklaşık **" + period + "** sonra tekrar deneyin.";
    }
}
