package com.warehouse.controller;

import com.warehouse.entity.User;
import com.warehouse.security.JwtService;
import com.warehouse.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        return userService.findByUsername(username)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .<ResponseEntity<?>>map(user -> {
                    Map<String, Object> resp = new HashMap<>();
                    String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
                    resp.put("token", token);
                    resp.put("username", user.getUsername());
                    resp.put("role", user.getRole().name());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials"));
    }
}


