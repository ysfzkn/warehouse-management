package com.warehouse.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            Map<String, Object> resp = new HashMap<>();
            // simple token: base64(username:password)
            String raw = username + ":" + password;
            String token = java.util.Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            resp.put("token", token);
            resp.put("username", username);
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }
}


