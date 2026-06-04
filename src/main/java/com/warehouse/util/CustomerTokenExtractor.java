package com.warehouse.util;

import com.warehouse.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class CustomerTokenExtractor {

    private CustomerTokenExtractor() {}

    public static Long extractCustomerId(HttpServletRequest request, JwtService jwtService) {
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = jwtService.parseToken(token);
                Number id = claims.get("customerId", Number.class);
                return id != null ? id.longValue() : null;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
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
