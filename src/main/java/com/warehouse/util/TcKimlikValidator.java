package com.warehouse.util;

/**
 * Turkish National ID (TC Kimlik No) validator.
 *
 * Rules:
 * - Exactly 11 digits
 * - First digit cannot be 0
 * - d10 = ((d1+d3+d5+d7+d9)*7 - (d2+d4+d6+d8)) mod 10
 * - d11 = (d1+d2+d3+d4+d5+d6+d7+d8+d9+d10) mod 10
 */
public final class TcKimlikValidator {

    private TcKimlikValidator() {}

    public static boolean isValid(String tcKimlikNo) {
        if (tcKimlikNo == null || tcKimlikNo.length() != 11) return false;
        if (!tcKimlikNo.matches("\\d{11}")) return false;
        if (tcKimlikNo.charAt(0) == '0') return false;

        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = tcKimlikNo.charAt(i) - '0';

        // 10th digit check
        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
        int evenSum = d[1] + d[3] + d[5] + d[7];
        int d10 = (oddSum * 7 - evenSum) % 10;
        if (d10 < 0) d10 += 10;
        if (d10 != d[9]) return false;

        // 11th digit check
        int totalSum = 0;
        for (int i = 0; i < 10; i++) totalSum += d[i];
        if (totalSum % 10 != d[10]) return false;

        return true;
    }
}
