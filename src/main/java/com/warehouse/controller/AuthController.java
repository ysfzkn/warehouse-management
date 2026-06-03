package com.warehouse.controller;

import com.warehouse.entity.User;
import com.warehouse.security.AdminLoginAttemptTracker;
import com.warehouse.security.JwtService;
import com.warehouse.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminLoginAttemptTracker loginTracker;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AdminLoginAttemptTracker loginTracker) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginTracker = loginTracker;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        // ── Brute-force lockout: per-user (IP-based limit is in RateLimitFilter) ──
        long lockedUntil = loginTracker.lockedUntilMillis(username);
        if (lockedUntil > 0) {
            long retryAfter = Math.max(1, (lockedUntil - System.currentTimeMillis()) / 1000);
            Map<String, Object> body429 = new HashMap<>();
            body429.put("error", "ACCOUNT_LOCKED");
            body429.put("message", "Çok fazla yanlış deneme. " + (retryAfter / 60) + " dakika sonra tekrar deneyin.");
            body429.put("retryAfter", retryAfter);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(body429);
        }

        return userService.findByUsername(username)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .<ResponseEntity<?>>map(user -> {
                    loginTracker.recordSuccess(username);
                    Map<String, Object> resp = new HashMap<>();
                    String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
                    resp.put("token", token);
                    resp.put("username", user.getUsername());
                    resp.put("role", user.getRole().name());
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> {
                    loginTracker.recordFailure(username);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(com.warehouse.constants.BusinessMessages.INVALID_CREDENTIALS);
                });
    }
}
