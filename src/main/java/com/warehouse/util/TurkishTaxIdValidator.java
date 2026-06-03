package com.warehouse.util;

/**
 * Algorithmic validator for the Turkish Tax ID (VKN) and Turkish National ID (TCKN).
 *
 * <p>If the ID number written for the recipient is wrong when issuing an
 * e-invoice/e-archive invoice, GİB rejects the invoice and Logo returns it in
 * ERROR status. In that case the "Your invoice is ready" email sent to the
 * customer is incorrect and a manual correction is required. With this validator
 * we verify the input right at the checkout stage, before sending it to Logo.</p>
 *
 * <p>Official algorithms:
 * <ul>
 *   <li><b>TCKN (11 digits):</b> (oddSum*7 - evenSum) mod 10 of the first 9 digits = 10th digit;
 *       sum of the first 10 digits mod 10 = 11th digit. The first digit cannot be zero.</li>
 *   <li><b>VKN (10 digits):</b> left-to-right position-based (vXVii) algorithm;
 *       the last digit is the checksum.</li>
 * </ul>
 */
public final class TurkishTaxIdValidator {

    private TurkishTaxIdValidator() {}

    /**
     * Returns whether the given string is valid as a TCKN (11 digits) or VKN
     * (10 digits). Blank/null is not accepted.
     */
    public static boolean isValid(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.length() == 11) return isValidTckn(trimmed);
        if (trimmed.length() == 10) return isValidVkn(trimmed);
        return false;
    }

    /** TCKN validation only. */
    public static boolean isValidTckn(String tckn) {
        if (tckn == null || tckn.length() != 11 || !tckn.chars().allMatch(Character::isDigit)) return false;
        if (tckn.charAt(0) == '0') return false;
        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = tckn.charAt(i) - '0';

        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];   // 1, 3, 5, 7, 9.
        int evenSum = d[1] + d[3] + d[5] + d[7];         // 2, 4, 6, 8.
        int d10 = ((oddSum * 7) - evenSum) % 10;
        if (d10 < 0) d10 += 10;
        if (d10 != d[9]) return false;

        int total = 0;
        for (int i = 0; i < 10; i++) total += d[i];
        return (total % 10) == d[10];
    }

    /** VKN validation only. */
    public static boolean isValidVkn(String vkn) {
        if (vkn == null || vkn.length() != 10 || !vkn.chars().allMatch(Character::isDigit)) return false;
        int[] v = new int[10];
        for (int i = 0; i < 10; i++) v[i] = vkn.charAt(i) - '0';

        long sum = 0;
        for (int i = 0; i < 9; i++) {
            int tmp = (v[i] + (9 - i)) % 10;
            if (tmp == 0) {
                sum += tmp;
            } else {
                // pow(2, 9-i) mod 9; if the result is 0, use 9
                long p = (long) (tmp * Math.pow(2, 9 - i));
                long mod = p % 9;
                if (p != 0 && mod == 0) mod = 9;
                sum += mod;
            }
        }
        int checksum = (int) ((10 - (sum % 10)) % 10);
        return checksum == v[9];
    }

    /** Quickly determines which of the two it is. */
    public enum TaxIdKind { TCKN, VKN, INVALID }

    public static TaxIdKind classify(String id) {
        if (id == null) return TaxIdKind.INVALID;
        String s = id.trim();
        if (s.length() == 11 && isValidTckn(s)) return TaxIdKind.TCKN;
        if (s.length() == 10 && isValidVkn(s))  return TaxIdKind.VKN;
        return TaxIdKind.INVALID;
    }
}
