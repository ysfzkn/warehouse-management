package com.warehouse.security;

import com.warehouse.constants.ApiPaths;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.AUTH, ApiPaths.ACTUATOR, ApiPaths.INFO, ApiPaths.ERROR).permitAll()
                        // SSE stream available to authenticated roles
                        .requestMatchers(ApiPaths.STREAM).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Stock viewing available to all authenticated users
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ApiPaths.STOCKS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Stock add operation - ADMIN and STOCK_IN
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/stocks/*/add").hasAnyRole("ADMIN", "STOCK_IN")
                        // Stock remove operation - ADMIN and STOCK_OUT
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/stocks/*/remove").hasAnyRole("ADMIN", "STOCK_OUT")
                        // Other stock operations - ADMIN only
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, ApiPaths.STOCKS).hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ApiPaths.STOCKS).hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, ApiPaths.STOCKS).hasRole("ADMIN")
                        // Stock requests - create/view for STOCK_IN and STOCK_OUT users
                        .requestMatchers("/api/stock-requests/**").hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Stock transfers available to all authenticated warehouse roles
                        .requestMatchers(ApiPaths.STOCK_TRANSFERS).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Excel operations only for ADMIN
                        .requestMatchers("/api/stock-imports/**").hasRole("ADMIN")
                        // Read-only supporting data for stock page
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                ApiPaths.PRODUCTS,
                                ApiPaths.WAREHOUSES,
                                ApiPaths.CATEGORIES,
                                ApiPaths.BRANDS,
                                ApiPaths.COLORS
                        ).hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
                        // Everything else admin-only
                        .requestMatchers(ApiPaths.ANY_API).hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}


