package com.warehouse.controller;

import com.warehouse.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthProperties authProperties;

    public AuthController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (authProperties.getUsername().equals(username) && 
            authProperties.getPassword().equals(password)) {
            Map<String, Object> resp = new HashMap<>();
            String raw = username + ":" + password;
            String token = java.util.Base64.getEncoder()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            resp.put("token", token);
            resp.put("username", username);
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }
}


