package com.warehouse.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class SimpleAuthFilter extends OncePerRequestFilter {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin}")
    private String adminPassword;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Public paths
        if (path.startsWith("/api/auth/") || path.startsWith("/actuator") || path.startsWith("/api/info") || path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String b64 = authHeader.substring(6).trim();
            try {
                String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                int idx = decoded.indexOf(':');
                if (idx > 0) {
                    String u = decoded.substring(0, idx);
                    String p = decoded.substring(idx + 1);
                    if (adminUsername.equals(u) && adminPassword.equals(p)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Unauthorized");
    }
}


