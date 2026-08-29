package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordPolicyValidator {

    private PasswordPolicyValidator() {}

    private static final int MIN_LENGTH = 8;
    /**
     * BCrypt only hashes the first 72 <em>bytes</em> of a password and silently ignores
     * the rest, so two different long passphrases can share a hash. Rejecting anything
     * beyond the limit is clearer than accepting it and quietly truncating — and it also
     * bounds the work an attacker can force per login attempt.
     */
    private static final int MAX_BYTES = 72;

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    /**
     * The passwords that show up first in every credential-stuffing list. A policy that
     * only counts character classes happily accepts "Password1", which is among the most
     * common passwords in existence.
     */
    private static final java.util.Set<String> COMMON_PASSWORDS = java.util.Set.of(
            "password1", "password123", "passw0rd", "qwerty123", "admin123", "12345678",
            "123456789", "1234567890", "iloveyou1", "welcome1", "abc12345", "letmein1",
            "sifre123", "parola123", "deneme123", "admin1234", "qwerty1234", "asdf1234"
    );

    public static void validate(String password) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("Sifre en az " + MIN_LENGTH + " karakter olmalidir");
        }
        if (password != null) {
            if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
                violations.add("Sifre en fazla " + MAX_BYTES + " bayt olabilir");
            }
            if (COMMON_PASSWORDS.contains(password.toLowerCase(java.util.Locale.ROOT))) {
                violations.add("Bu sifre cok yaygin kullaniliyor, lutfen baska bir sifre secin");
            }
            if (!UPPERCASE.matcher(password).find()) {
                violations.add("Sifre en az 1 buyuk harf icermelidir");
            }
            if (!LOWERCASE.matcher(password).find()) {
                violations.add("Sifre en az 1 kucuk harf icermelidir");
            }
            if (!DIGIT.matcher(password).find()) {
                violations.add("Sifre en az 1 rakam icermelidir");
            }
        }

        if (!violations.isEmpty()) {
            throw new WarehouseManagementException(
                ErrorCode.VALIDATION_ERROR,
                String.join(". ", violations)
            );
        }
    }
}
