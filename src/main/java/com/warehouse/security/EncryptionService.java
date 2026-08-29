package com.warehouse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for the handful of columns that must not be readable from a
 * database dump: national identity numbers and payment-gateway credentials.
 *
 * <p>Key material comes from {@code APP_ENCRYPTION_KEY} (base64, 32 bytes). When that
 * is absent the key is derived from {@code JWT_SECRET} with a fixed domain-separation
 * label, so the application still encrypts out of the box and a deployment cannot
 * silently fall back to storing plaintext. Production already refuses to boot with a
 * weak {@code JWT_SECRET}, so the derived key inherits that strength — but a dedicated
 * {@code APP_ENCRYPTION_KEY} is preferable because it lets the JWT secret be rotated
 * without re-encrypting the database.</p>
 *
 * <p>Ciphertext format: {@code enc:v1:} + base64(iv ‖ ciphertext ‖ tag). The prefix
 * lets {@link #decrypt(String)} recognise values written before this change and return
 * them unchanged, so the migration is transparent: rows are re-encrypted lazily as they
 * are next written.</p>
 */
@Component
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public EncryptionService(@Value("${app.security.encryption-key:}") String configuredKey,
                             @Value("${app.security.jwt-secret:}") String jwtSecret) {
        this.key = resolveKey(configuredKey, jwtSecret);
    }

    private SecretKey resolveKey(String configuredKey, String jwtSecret) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            try {
                byte[] raw = Base64.getDecoder().decode(configuredKey.trim());
                if (raw.length == 32) {
                    return new SecretKeySpec(raw, "AES");
                }
                log.warn("APP_ENCRYPTION_KEY 32 bayt (base64) olmalı — {} bayt geldi; JWT secret'tan türetiliyor.",
                        raw.length);
            } catch (IllegalArgumentException e) {
                log.warn("APP_ENCRYPTION_KEY base64 değil; JWT secret'tan türetiliyor.");
            }
        }
        return deriveFrom(jwtSecret);
    }

    private SecretKey deriveFrom(String jwtSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("warehouse:column-encryption:v1".getBytes(StandardCharsets.UTF_8));
            digest.update((jwtSecret == null ? "" : jwtSecret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest.digest(), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Şifreleme anahtarı türetilemedi", e);
        }
    }

    /** Returns null for null input; already-encrypted values are returned untouched. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (plaintext.startsWith(PREFIX)) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Veri şifrelenemedi", e);
        }
    }

    /** Values without the {@code enc:v1:} prefix predate encryption and pass through. */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] blob = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] pt = cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // A key change makes old rows undecryptable. Failing the whole request would
            // take the admin panel down; returning null degrades that one field instead.
            log.error("Şifreli alan çözülemedi (anahtar değişmiş olabilir): {}", e.getMessage());
            return null;
        }
    }

    /**
     * One-way hash for bearer secrets that only ever need equality comparison
     * (refresh tokens, password-reset tokens). Unlike encryption this cannot be
     * reversed by whoever obtains the database.
     */
    public static String hashToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token hash'lenemedi", e);
        }
    }
}
