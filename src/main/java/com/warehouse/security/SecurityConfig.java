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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
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
    private final HostValidationFilter hostValidationFilter;

    @org.springframework.beans.factory.annotation.Value("${CORS_ALLOWED_ORIGINS:http://localhost,http://localhost:*,https://localhost:*}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          CustomerStatusCheckFilter customerStatusCheckFilter,
                          HostValidationFilter hostValidationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.customerStatusCheckFilter = customerStatusCheckFilter;
        this.hostValidationFilter = hostValidationFilter;
    }

    /**
     * Applies production security headers to every filter chain from a single place.
     *
     * <ul>
     *   <li><b>HSTS</b> — enforces HTTPS (1 year, includes subdomains, preload)</li>
     *   <li><b>X-Content-Type-Options: nosniff</b> — protects against MIME-sniffing attacks</li>
     *   <li><b>X-Frame-Options: DENY</b> — clickjacking protection (backup for CSP frame-ancestors)</li>
     *   <li><b>Referrer-Policy</b> — strict-origin-when-cross-origin (reduces default leakage)</li>
     *   <li><b>Permissions-Policy</b> — camera/microphone/geolocation and similar APIs disabled by default</li>
     *   <li><b>CSP</b> — XSS defense; allows 'unsafe-inline' style for React needs, script-src self + analytics + iyzico/PayTR; can be migrated to nonce-based later.</li>
     * </ul>
     */
    private void applySecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000) // 1 year
                )
                .contentTypeOptions(opts -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .addHeaderWriter((req, resp) -> {
                    // Permissions-Policy: aggressive default-deny (the e-commerce site does not need these APIs)
                    resp.setHeader("Permissions-Policy",
                            "geolocation=(), camera=(), microphone=(), payment=(self), usb=(), fullscreen=(self)");
                    // Content-Security-Policy: a practical starting point compatible with both e-commerce and admin.
                    // Inline style is required on the React side (CSS-in-JS, Helmet); script-src 'self' + analytics + payment gateways.
                    if (!resp.containsHeader("Content-Security-Policy")) {
                        resp.setHeader("Content-Security-Policy",
                                "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' https://www.googletagmanager.com https://connect.facebook.net https://static.iyzipay.com https://www.iyzico.com https://www.paytr.com https://kargonomi.com.tr; " +
                                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com; " +
                                "font-src 'self' data: https://cdnjs.cloudflare.com https://fonts.gstatic.com; " +
                                "img-src 'self' data: blob: https:; " +
                                "connect-src 'self' https://www.google-analytics.com https://api.iyzipay.com https://api.kargonomi.com.tr; " +
                                "frame-src 'self' https://www.iyzico.com https://static.iyzipay.com https://www.paytr.com; " +
                                "frame-ancestors 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self' https://www.iyzipay.com https://sandbox-api.iyzipay.com https://www.paytr.com;");
                    }
                })
        );
    }

    /**
     * Admin filter chain — serves the existing WMS dashboard.
     * All existing role-based rules preserved under /api/admin/** prefix.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        applySecurityHeaders(http);
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
                // Brute-force protection for admin auth login (RateLimitService
                // defines a limit of 5 attempts within 15 minutes for /api/admin/auth/login).
                .addFilterBefore(hostValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
        applySecurityHeaders(http);
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
                        // Guest checkout: public (guest users can place an order without signing up)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_GUEST_CHECKOUT).permitAll()
                        // Guest order payment initialization: public (protected by orderId + idempotencyKey)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PAYMENT_INITIALIZE).permitAll()
                        // Public order tracking: protected by orderNumber + email combination
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PUBLIC_ORDER_TRACK).permitAll()
                        // Notify when out of stock: guests can subscribe too
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PRODUCT_NOTIFY_ME).permitAll()
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
                .addFilterBefore(hostValidationFilter, UsernamePasswordAuthenticationFilter.class)
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
        applySecurityHeaders(http);
        http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health, /actuator/info, /info, /error → public
                        .requestMatchers(ApiPaths.ACTUATOR, ApiPaths.INFO, ApiPaths.ERROR).permitAll()
                        // All other actuator endpoints (env, configprops, loggers, beans, metrics, prometheus)
                        // are ADMIN only — to avoid leaking secrets and internal state.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Swagger UI + OpenAPI docs — ADMIN only
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Payment / webhook callback endpoints: restricted whitelist.
        // When Iyzico, PayTR, Logo eLogo, and Kargonomi make server-to-server POSTs
        // they usually do NOT send an Origin header (no CORS preflight needed); this CORS
        // config is only for iframe / redirect returns opened from the browser.
        CorsConfiguration callbackConfig = new CorsConfiguration();
        callbackConfig.setAllowedOriginPatterns(List.of(
                "https://*.iyzipay.com",
                "https://*.iyzico.com",
                "https://*.paytr.com",
                "https://*.kargonomi.com.tr",
                "https://*.elogo.com.tr",
                "https://*.efinans.com.tr"
        ));
        callbackConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        callbackConfig.setAllowedHeaders(List.of("Content-Type", "X-Signature"));
        callbackConfig.setAllowCredentials(false);

        // Regular API endpoints: restricted origins
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsAllowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Session-Id",
                "X-ADMIN-SECURITY-CODE", "X-Requested-With", "Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register callback paths FIRST (more specific paths take precedence)
        source.registerCorsConfiguration("/api/store/payment/callback", callbackConfig);
        source.registerCorsConfiguration("/api/store/payment/callback/**", callbackConfig);
        // Webhooks (Kargonomi, Logo): behave like callbacks
        source.registerCorsConfiguration("/api/admin/cargo/webhook/**", callbackConfig);
        source.registerCorsConfiguration("/api/admin/invoice/webhook/**", callbackConfig);
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
