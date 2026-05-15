package com.warehouse.security;

import com.warehouse.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
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

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    // Lansman öncesi taranan zayıf/default secret blacklist'i — prod'da bunlardan
    // birinin kullanılması fatal hata olur (fail-fast at startup).
    private static final java.util.Set<String> WEAK_SECRETS = java.util.Set.of(
            "change-this-secret",
            "dev-secret",
            "dev-secret-change-in-production-min-32-chars!!",
            "secret",
            "your-secret-key",
            "your-256-bit-secret"
    );

    private final SecurityProperties securityProperties;
    private final Key signingKey;

    public JwtService(SecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        byte[] keyBytes;
        String secret = Objects.toString(securityProperties.getJwtSecret(), "change-this-secret");

        // ── Production fail-fast: zayıf/default secret prod'da kabul edilmez ──
        boolean isProd = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                isProd = true; break;
            }
        }
        if (isProd) {
            String trimmed = secret == null ? "" : secret.trim();
            if (trimmed.isBlank() || WEAK_SECRETS.contains(trimmed) || trimmed.length() < 32) {
                String msg = "FATAL: Production'da JWT_SECRET zayıf/eksik. " +
                             "En az 32 karakterli, default olmayan bir secret env değişkeni olarak verilmelidir. " +
                             "Önerilen: openssl rand -base64 48";
                log.error(msg);
                throw new IllegalStateException(msg);
            }
        }
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

    /**
     * Generate a JWT for B2C customer authentication.
     * Includes userType=customer claim for filter chain routing.
     */
    public String generateCustomerToken(Long customerId, String email) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime exp = now.plusDays(securityProperties.getCustomerTokenExpirationDays());
        return Jwts.builder()
                .setSubject(email)
                .addClaims(Map.of(
                    "customerId", customerId,
                    "userType", "customer"
                ))
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(exp.toInstant()))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long extractCustomerId(String token) {
        try {
            Claims claims = parseToken(token);
            Object cid = claims.get("customerId");
            if (cid instanceof Number) return ((Number) cid).longValue();
            if (cid instanceof String) return Long.parseLong((String) cid);
            return null;
        } catch (Exception e) { return null; }
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}


