package com.warehouse.security;

import com.warehouse.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecurityProperties securityProperties;
    private final Key signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        byte[] keyBytes;
        String secret = Objects.toString(securityProperties.getJwtSecret(), "change-this-secret");
        // Accept raw text or base64 secret
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException | DecodingException e) {
            keyBytes = secret.getBytes();
        }
        Key key;
        try {
            // Ensure minimum 256-bit key for HS256
            if (keyBytes.length < 32) {
                try {
                    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                    keyBytes = Arrays.copyOf(sha256.digest(keyBytes), 32);
                } catch (Exception ignored) {
                    keyBytes = Arrays.copyOf(keyBytes, 32);
                }
            }
            key = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            // As ultimate fallback, generate a secure random key
            key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
        this.signingKey = key;
    }

    public String generateToken(String username, String role) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime exp = now.plusMinutes(securityProperties.getJwtExpirationMinutes());
        return Jwts.builder()
                .setSubject(username)
                .addClaims(Map.of("role", role))
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(exp.toInstant()))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}


