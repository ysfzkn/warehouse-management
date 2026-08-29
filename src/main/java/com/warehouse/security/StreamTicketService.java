package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Single-use, short-lived tickets for the SSE stream.
 *
 * <p>{@code EventSource} cannot set an {@code Authorization} header, so the stream
 * used to be opened with the full admin JWT in the query string. Query strings are
 * written to nginx/Railway access logs, kept in browser history and leaked through
 * {@code Referer} — a long-lived admin token has no business being there.</p>
 *
 * <p>Instead the client exchanges its Bearer token for a ticket over a normal
 * authenticated POST, then opens the stream with {@code ?ticket=...}. The ticket is
 * valid for one minute, can only be redeemed once, and grants nothing beyond the
 * stream subscription it was minted for.</p>
 */
@Service
public class StreamTicketService {

    private static final Duration TTL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** ticket → the authenticated principal it was issued for. */
    private final Cache<String, TicketOwner> tickets = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(50_000)
            .build();

    public record TicketOwner(String username, String role) {}

    public String issue(String username, String role) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(ticket, new TicketOwner(username, role));
        return ticket;
    }

    /** Redeems the ticket, invalidating it. Returns null when unknown or already used. */
    public TicketOwner redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        TicketOwner owner = tickets.getIfPresent(ticket);
        if (owner != null) {
            tickets.invalidate(ticket);
        }
        return owner;
    }

    public long ttlSeconds() {
        return TTL.getSeconds();
    }
}
