package com.warehouse.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String STREAM_PATH = "/api/admin/stream";

    /**
     * Roles that may ever be granted from a JWT. Without this, a token carrying an
     * arbitrary {@code role} claim would be translated into an arbitrary
     * {@code ROLE_*} authority; anything not in this set is rejected outright.
     */
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "STOCK_IN", "STOCK_OUT");

    private final JwtService jwtService;
    private final StreamTicketService streamTicketService;

    public JwtAuthenticationFilter(JwtService jwtService, StreamTicketService streamTicketService) {
        this.jwtService = jwtService;
        this.streamTicketService = streamTicketService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // SSE: EventSource cannot send headers, so the client redeems a single-use
        // ticket instead of putting a long-lived JWT in the query string.
        if (STREAM_PATH.equals(request.getRequestURI())) {
            StreamTicketService.TicketOwner owner = streamTicketService.redeem(request.getParameter("ticket"));
            if (owner != null) {
                authenticate(owner.username(), roleAuthorities(owner.role()));
                filterChain.doFilter(request, response);
                return;
            }
        }

        String token = null;
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // Fallback: read from HttpOnly cookie (storefront)
            token = extractTokenFromCookie(request);
        }
        if (token != null) {
            try {
                Claims claims = jwtService.parseToken(token);
                String username = claims.getSubject();
                String userType = claims.get("userType", String.class);
                List<GrantedAuthority> authorities;
                if ("customer".equals(userType)) {
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                } else {
                    // "userType" was only added to admin tokens later, so tokens minted
                    // before this change carry no userType at all — those are still
                    // admin tokens and must keep working until they expire.
                    authorities = roleAuthorities(claims.get("role", String.class));
                }
                if (!authorities.isEmpty()) {
                    authenticate(username, authorities);
                }
            } catch (Exception e) {
                // invalid/expired/revoked token; proceed unauthenticated and let the
                // authorization rules decide (they will reject protected endpoints).
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, List<GrantedAuthority> authorities) {
        if (username == null || authorities.isEmpty()) return;
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private List<GrantedAuthority> roleAuthorities(String role) {
        if (role == null) return List.of();
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            return List.of();
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalized));
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
