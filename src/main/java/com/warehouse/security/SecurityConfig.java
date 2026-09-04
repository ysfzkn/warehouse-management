package com.warehouse.security;

import com.warehouse.constants.ApiPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
     *   <li><b>CSP</b> — see {@link #contentSecurityPolicy()}</li>
     * </ul>
     *
     * <p>Note: these headers only cover responses produced by this application. The
     * SPA's HTML document is served by nginx, so the identical policy is mirrored in
     * {@code nginx/prod.conf} and {@code frontend/nginx.conf} — without that, the
     * browser never receives a CSP for the page that actually executes scripts.</p>
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
                    if (!resp.containsHeader("Content-Security-Policy")) {
                        resp.setHeader("Content-Security-Policy", contentSecurityPolicy());
                    }
                })
        );
    }

    /**
     * Content-Security-Policy shared by the API and the nginx-served SPA.
     *
     * <p>{@code script-src} still carries {@code 'unsafe-inline'} because the consent-gated
     * analytics snippets (GA4, Meta Pixel, Hotjar, Clarity) and the payment HTML that
     * iyzico/PayTR return are injected inline at runtime. Removing it requires migrating
     * those to nonce or hash based loading, which cannot be done from the backend alone
     * while the SPA is a static bundle. The previous policy also silently omitted
     * {@code cdn.jsdelivr.net} — the host that serves Bootstrap's CSS and JS — so it was
     * broken as well as permissive; the host lists below reflect what the app actually loads.
     */
    private static String contentSecurityPolicy() {
        return "default-src 'self'; "
                + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://www.googletagmanager.com "
                + "https://connect.facebook.net https://static.hotjar.com https://script.hotjar.com "
                + "https://www.clarity.ms https://static.iyzipay.com https://www.iyzico.com "
                + "https://www.paytr.com https://kargonomi.com.tr; "
                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; "
                + "font-src 'self' data: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.gstatic.com; "
                + "img-src 'self' data: blob: https:; "
                + "media-src 'self' https:; "
                + "connect-src 'self' https://www.google-analytics.com https://region1.google-analytics.com "
                + "https://connect.facebook.net https://*.hotjar.com https://*.hotjar.io wss://*.hotjar.com "
                + "https://www.clarity.ms https://api.iyzipay.com https://api.kargonomi.com.tr; "
                + "frame-src 'self' https://www.iyzico.com https://static.iyzipay.com https://www.paytr.com https://www.google.com; "
                // object/embed can execute script in some browsers and nothing here uses them.
                + "object-src 'none'; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self' https://www.iyzipay.com https://sandbox-api.iyzipay.com https://www.paytr.com; "
                + "upgrade-insecure-requests";
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
                // CSRF is not needed: the admin panel authenticates with a Bearer
                // header, which a cross-site form or image tag cannot set.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.ADMIN_AUTH).permitAll()
                        // SSE stream + its single-use ticket endpoint
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
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/returns/photos/*/view").permitAll()
                        // Signed delivery-receipt scans: rendered in <img>/<iframe>, so the
                        // signature in the URL is the authorisation (see SignedUrlService).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/admin/delivery-receipts/attachments/*/view").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/settings/site/asset/view/**").permitAll()
                        // Cargo and e-invoice provider webhooks: server-to-server, authenticated
                        // by HMAC signature inside the controller rather than by a session.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/cargo/webhook/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/invoice/webhook/**").permitAll()
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
                        // Driver and vehicle directories: warehouse roles are the ones filling in
                        // transfers, so they have to be able to search and pick from both. Without
                        // this the type-ahead fell through to the admin-only rule below and every
                        // lookup came back 403 — which the picker could only render as "no records".
                        // Editing and deleting stay admin-only, enforced by @PreAuthorize on the
                        // controllers; only the vehicle create endpoint is opened up, because a new
                        // plate usually turns up mid-transfer.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                ApiPaths.ADMIN_DRIVERS,
                                ApiPaths.ADMIN_VEHICLES
                        ).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                ApiPaths.ADMIN_VEHICLES_CREATE
                        ).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Everything else admin-only
                        .requestMatchers(ApiPaths.ADMIN_ANY).hasRole("ADMIN")
                        .anyRequest().denyAll()
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
                // The storefront cookie is SameSite=Lax, so a cross-site POST never
                // carries it; combined with the Bearer fallback this leaves no CSRF
                // surface for state-changing calls.
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
                        // Guest order payment initialization: public, but the caller must prove
                        // ownership of the order with the payment token issued at checkout
                        // (see PaymentServiceImpl#initializePayment).
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PAYMENT_INITIALIZE).permitAll()
                        // Public order tracking: protected by orderNumber + email combination
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PUBLIC_ORDER_TRACK).permitAll()
                        .requestMatchers("/api/store/public/orders/confirm/**").permitAll()
                        // Notify when out of stock: guests can subscribe too
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PRODUCT_NOTIFY_ME).permitAll()
                        // Public popularity ping + recently-viewed batch hydration (no PII, no auth)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PRODUCT_TRACK_VIEW).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_PRODUCTS_BY_IDS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STORE_NEWSLETTER).permitAll()
                        // Public settings and banners
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STORE_SETTINGS).permitAll()
                        // Review photos are shown on public product pages, so the image
                        // view must not require a session (the list itself is already
                        // public via GET /api/store/products/**). Writing stays gated.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/store/reviews/images/*/view").permitAll()
                        // Cart: guest carts are keyed by X-Session-Id, so they cannot require auth
                        .requestMatchers(ApiPaths.STORE_CART).permitAll()
                        // Contact form: guests must be able to submit it. It carries a
                        // honeypot field and a dedicated rate-limit bucket, both of which
                        // only make sense for an unauthenticated endpoint — but the rule
                        // was missing, so every guest submission fell through to the
                        // CUSTOMER rule below and was rejected.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/store/contact-messages").permitAll()
                        // Store assistant (Cezeri v2): guest + customer. Rate limits + guard
                        // handle abuse. Authentication is resolved inside the controller via
                        // the JWT filter + StoreAssistantGuard cookie flow.
                        .requestMatchers(ApiPaths.STORE_ASSISTANT).permitAll()
                        // Everything else requires CUSTOMER role
                        .requestMatchers(ApiPaths.STORE_ANY).hasRole("CUSTOMER")
                        .anyRequest().denyAll()
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
     * Catch-all chain for everything outside {@code /api/admin}, {@code /api/store} and
     * {@code /api/cezeri}.
     *
     * <p>This used to end in {@code anyRequest().permitAll()}, which made the default
     * for any newly mounted controller "world readable" — that is exactly how the
     * Kargonomi cargo webhook ended up reachable by anyone on the internet. It now ends
     * in {@code denyAll()}: a new endpoint is unreachable until it is deliberately
     * listed here, so the failure mode is a 403 during development rather than an open
     * door in production. The rate-limit and host-validation filters run here too.</p>
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
                        // Liveness/readiness + build info + Spring's error dispatch
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                ApiPaths.INFO, ApiPaths.ERROR).permitAll()
                        // SEO endpoints
                        .requestMatchers("/sitemap.xml", "/sitemap-*.xml", "/robots.txt", "/favicon.ico").permitAll()
                        // Assistant feature flags consumed by both storefront and admin shells
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/assistant/flags/**").permitAll()
                        // Cargo provider webhook — authenticated by HMAC signature in the controller
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/public/cargo/**").permitAll()
                        // CORS preflight must never require credentials
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // All other actuator endpoints (env, configprops, loggers, beans, metrics,
                        // prometheus) are ADMIN only — to avoid leaking secrets and internal state.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Swagger UI + OpenAPI docs — ADMIN only
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(new SilentAccessDeniedHandler())
                )
                .addFilterBefore(hostValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
        config.setAllowedOriginPatterns(sanitizedAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Session-Id",
                "X-ADMIN-SECURITY-CODE", "X-Requested-With", "Idempotency-Key", "X-Captcha-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register callback paths FIRST (more specific paths take precedence)
        source.registerCorsConfiguration("/api/store/payment/callback", callbackConfig);
        source.registerCorsConfiguration("/api/store/payment/callback/**", callbackConfig);
        // Webhooks (Kargonomi, Logo): behave like callbacks
        source.registerCorsConfiguration("/api/admin/cargo/webhook/**", callbackConfig);
        source.registerCorsConfiguration("/api/admin/invoice/webhook/**", callbackConfig);
        source.registerCorsConfiguration("/api/public/cargo/**", callbackConfig);
        // Then general API paths
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Parses {@code CORS_ALLOWED_ORIGINS}, rejecting wildcards.
     *
     * <p>{@code allowedOriginPatterns} combined with {@code allowCredentials(true)} will
     * happily echo back any origin that matches {@code *}. A single mistyped environment
     * variable would therefore let every website on the internet make authenticated
     * requests on behalf of a logged-in customer or administrator. A wildcard entry is
     * dropped with a loud warning rather than honoured.</p>
     */
    private List<String> sanitizedAllowedOrigins() {
        List<String> result = new ArrayList<>();
        for (String raw : Arrays.asList(corsAllowedOrigins.split(","))) {
            String origin = raw.trim();
            if (origin.isEmpty()) continue;
            if ("*".equals(origin) || "*/*".equals(origin) || "https://*".equals(origin)
                    || "http://*".equals(origin)) {
                log.error("CORS_ALLOWED_ORIGINS içinde joker (\"{}\") bulundu ve YOK SAYILDI. "
                        + "allowCredentials=true ile birlikte bu, her siteye kimlikli istek izni vermek demektir. "
                        + "Lütfen tam origin listesi verin (örn: https://www.example.com).", origin);
                continue;
            }
            result.add(origin);
        }
        if (result.isEmpty()) {
            log.error("CORS_ALLOWED_ORIGINS boş/geçersiz — tarayıcıdan gelen çapraz origin istekleri reddedilecek.");
            // An empty list means "no cross-origin browser access", which is the safe
            // failure mode: same-origin calls (the normal deployment) still work.
            result.add("https://localhost");
        }
        return result;
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
