package com.warehouse.util;

/**
 * Türkiye Vergi Kimlik No (VKN) ve T.C. Kimlik No (TCKN) algoritmik doğrulayıcı.
 *
 * <p>E-fatura/e-arşiv kesilirken alıcıya yazılan kimlik numarası yanlışsa GİB
 * faturayı reject eder ve Logo'dan ERROR statüsünde geri döner. Bu durumda
 * müşteriye gönderilen "Faturanız Hazır" e-postası yanlış olur ve manuel
 * düzeltme gerekir. Bu validator ile başvuruyu Logo'ya göndermeden hemen
 * checkout aşamasında doğrularız.</p>
 *
 * <p>Resmi algoritmalar:
 * <ul>
 *   <li><b>TCKN (11 hane):</b> İlk 9 hanenin oddSum*7 - evenSum mod 10 = 10. hane;
 *       İlk 10 hanenin toplamı mod 10 = 11. hane. İlk hane sıfır olamaz.</li>
 *   <li><b>VKN (10 hane):</b> Soldan sağa pozisyon-bazlı (vXVii) algoritması;
 *       son hane checksum.</li>
 * </ul>
 */
public final class TurkishTaxIdValidator {

    private TurkishTaxIdValidator() {}

    /**
     * Verilen string'in TCKN (11 hane) veya VKN (10 hane) olarak geçerli olup
     * olmadığını döner. Boş/null kabul edilmez.
     */
    public static boolean isValid(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.length() == 11) return isValidTckn(trimmed);
        if (trimmed.length() == 10) return isValidVkn(trimmed);
        return false;
    }

    /** Sadece TCKN doğrulaması. */
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

    /** Sadece VKN doğrulaması. */
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
                // pow(2, 9-i) mod 9; eğer sonuç 0 ise 9
                long p = (long) (tmp * Math.pow(2, 9 - i));
                long mod = p % 9;
                if (p != 0 && mod == 0) mod = 9;
                sum += mod;
            }
        }
        int checksum = (int) ((10 - (sum % 10)) % 10);
        return checksum == v[9];
    }

    /** İkisinden hangisi olduğunu hızlıca belirler. */
    public enum TaxIdKind { TCKN, VKN, INVALID }

    public static TaxIdKind classify(String id) {
        if (id == null) return TaxIdKind.INVALID;
        String s = id.trim();
        if (s.length() == 11 && isValidTckn(s)) return TaxIdKind.TCKN;
        if (s.length() == 10 && isValidVkn(s))  return TaxIdKind.VKN;
        return TaxIdKind.INVALID;
    }
}
