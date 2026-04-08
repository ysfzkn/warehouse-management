package com.warehouse.security;

import com.warehouse.constants.ApiPaths;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomerStatusCheckFilter customerStatusCheckFilter;

    @org.springframework.beans.factory.annotation.Value("${CORS_ALLOWED_ORIGINS:http://localhost,http://localhost:*,https://localhost:*}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          CustomerStatusCheckFilter customerStatusCheckFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.customerStatusCheckFilter = customerStatusCheckFilter;
    }

    /**
     * Admin filter chain — serves the existing WMS dashboard.
     * All existing role-based rules preserved under /api/admin/** prefix.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                // Include /api/cezeri/** so the WMS assistant goes through the
                // admin auth pipeline (rate limits, JWT parsing, role check).
                // The controller URL is preserved from v1 for frontend compat.
                .securityMatcher("/api/admin/**", "/api/cezeri/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.ADMIN_AUTH).permitAll()
                        // SSE stream available to authenticated warehouse roles
                        .requestMatchers(ApiPaths.ADMIN_STREAM).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Cezeri WMS AI assistant
                        .requestMatchers(ApiPaths.CEZERI).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Admin assistant management (doc uploads, dashboard, logs) — admin only
                        .requestMatchers(ApiPaths.ADMIN_ASSISTANT).hasRole("ADMIN")
                        // Stock viewing available to all authenticated users
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.ADMIN_STOCKS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Stock add operation - ADMIN and STOCK_IN
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/admin/stocks/*/add").hasAnyRole("ADMIN", "STOCK_IN")
                        // Stock remove operation - ADMIN and STOCK_OUT
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/admin/stocks/*/remove").hasAnyRole("ADMIN", "STOCK_OUT")
                        // Other stock operations - ADMIN only
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, ApiPaths.ADMIN_STOCKS).hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.ADMIN_STOCKS).hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, ApiPaths.ADMIN_STOCKS).hasRole("ADMIN")
                        // Stock requests - create/view for all warehouse roles
                        .requestMatchers(ApiPaths.ADMIN_STOCK_REQUESTS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Stock transfers available to all authenticated warehouse roles
                        .requestMatchers(ApiPaths.ADMIN_STOCK_TRANSFERS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Public image viewing
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/stock-transfer-items/*/photo/view").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/products/images/*/view").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/settings/site/asset/view/**").permitAll()
                        // Stock transfer item photo operations
                        .requestMatchers(ApiPaths.ADMIN_STOCK_TRANSFER_ITEMS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Excel operations only for ADMIN
                        .requestMatchers(ApiPaths.ADMIN_STOCK_IMPORTS).hasRole("ADMIN")
                        // Read-only supporting data for stock page
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                ApiPaths.ADMIN_PRODUCTS,
                                ApiPaths.ADMIN_WAREHOUSES,
                                ApiPaths.ADMIN_CATEGORIES,
                                ApiPaths.ADMIN_BRANDS,
                                ApiPaths.ADMIN_COLORS
                        ).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Everything else admin-only
                        .requestMatchers(ApiPaths.ADMIN_ANY).hasRole("ADMIN")
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(new SilentAccessDeniedHandler())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Store filter chain — serves the B2C e-commerce storefront.
     * Public catalog endpoints + ROLE_CUSTOMER for authenticated operations.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain storeFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/store/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no auth required)
                        .requestMatchers(ApiPaths.STORE_AUTH).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_PRODUCTS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_CATEGORIES).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_BRANDS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_COLORS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_PAGES).permitAll()
                        .requestMatchers(ApiPaths.STORE_PAYMENT_CALLBACK).permitAll()
                        .requestMatchers(ApiPaths.STORE_PAYMENT_CALLBACK_POS).permitAll()
                        .requestMatchers(ApiPaths.STORE_PAYMENT_CALLBACK_PAYTR).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_PAYMENT_METHODS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_PAYMENT_STATUS_TOKEN).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_CARGO_PROVIDERS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_NEWSLETTER).permitAll()
                        // Public settings and banners
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_SETTINGS).permitAll()
                        // Cart: GET is public (guest carts via session), mutations require customer or session
                        .requestMatchers(ApiPaths.STORE_CART).permitAll()
                        // Store assistant (Cezeri v2): guest + customer. Rate limits + guard
                        // handle abuse. Authentication is resolved inside the controller via
                        // the JWT filter + StoreAssistantGuard cookie flow.
                        .requestMatchers(ApiPaths.STORE_ASSISTANT).permitAll()
                        // Everything else requires CUSTOMER role
                        .requestMatchers(ApiPaths.STORE_ANY).hasRole("CUSTOMER")
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(new SilentAccessDeniedHandler())
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(customerStatusCheckFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Public filter chain — health checks, info, static assets.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.ACTUATOR, ApiPaths.INFO, ApiPaths.ERROR).permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Payment callback endpoints: allow ALL origins (iyzico, PayTR, bank servers POST here)
        CorsConfiguration callbackConfig = new CorsConfiguration();
        callbackConfig.setAllowedOriginPatterns(List.of("*"));
        callbackConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        callbackConfig.setAllowedHeaders(List.of("*"));
        callbackConfig.setAllowCredentials(false);

        // Regular API endpoints: restricted origins
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsAllowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Session-Id", "X-ADMIN-SECURITY-CODE", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register callback paths FIRST (more specific paths take precedence)
        source.registerCorsConfiguration("/api/store/payment/callback", callbackConfig);
        source.registerCorsConfiguration("/api/store/payment/callback/**", callbackConfig);
        // Then general API paths
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static class SilentAccessDeniedHandler implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                          AccessDeniedException accessDeniedException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
