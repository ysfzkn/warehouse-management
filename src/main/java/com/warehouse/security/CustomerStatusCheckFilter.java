package com.warehouse.security;

import com.warehouse.entity.Customer;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.repository.CustomerRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CustomerStatusCheckFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomerRepository customerRepository;

    public CustomerStatusCheckFilter(JwtService jwtService, CustomerRepository customerRepository) {
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Only filter store endpoints that require authentication (not auth endpoints)
        return !uri.startsWith("/api/store/") || uri.startsWith("/api/store/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            // Check if this is a customer token
            String token = extractToken(request);
            if (token != null) {
                try {
                    Claims claims = jwtService.parseToken(token);
                    String userType = claims.get("userType", String.class);
                    if ("customer".equals(userType)) {
                        Number customerIdNum = claims.get("customerId", Number.class);
                        if (customerIdNum != null) {
                            Long customerId = customerIdNum.longValue();
                            Customer customer = customerRepository.findById(customerId).orElse(null);
                            if (customer != null && customer.getStatus() == CustomerStatus.BLACKLISTED) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write(
                                    "{\"error\":\"ACCOUNT_BLACKLISTED\",\"message\":\"Hesabiniz kara listeye alinmistir. Destek ile iletisime gecin.\"}"
                                );
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Token parse failure handled by JwtAuthenticationFilter
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Check cookies
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
