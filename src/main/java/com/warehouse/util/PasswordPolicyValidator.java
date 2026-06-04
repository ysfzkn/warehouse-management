package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordPolicyValidator {

    private PasswordPolicyValidator() {}

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    public static void validate(String password) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("Sifre en az " + MIN_LENGTH + " karakter olmalidir");
        }
        if (password != null) {
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
