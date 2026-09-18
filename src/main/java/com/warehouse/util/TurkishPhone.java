package com.warehouse.util;

/**
 * A Turkish phone number in the form carriers and SMS gateways ask for: ten digits, no country
 * code, no leading zero — {@code 5321112233}.
 *
 * <p>This rule was written four times over, each slightly different: the cargo provider dropped
 * the prefix only when the length matched exactly, the SMS provider dropped it unconditionally,
 * the address controller <em>added</em> a zero instead, and {@link TurkishText#normalizePhone}
 * keeps the last ten digits because comparing two records is a different question from dialling
 * one. Four rules meant a number accepted at checkout could be refused at dispatch, which is what
 * happened: Kargonomi rejected a shipment with "Gönderici Telefon 1 (Mobil) 10 rakam olmalıdır"
 * for a number every other screen had shown as valid.
 *
 * <p>The reduction is deliberately conservative. A prefix is removed only when what remains is
 * the right length, so a number carrying an extension or a second number is left long rather
 * than trimmed into a plausible-looking wrong one — {@link #isValid} then rejects it and the
 * caller can say so.
 */
public final class TurkishPhone {

    private TurkishPhone() {}

    /** How many digits a Turkish number has once the country code and trunk zero are gone. */
    public static final int NATIONAL_LENGTH = 10;

    /**
     * "+90 532 111 22 33", "0532 111 22 33", "0090 532 111 22 33" and "5321112233" all reduce to
     * {@code 5321112233}.
     *
     * @return the digits that remain; not necessarily {@value #NATIONAL_LENGTH} long — check with
     *         {@link #isValid} before sending it anywhere
     */
    public static String national(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == NATIONAL_LENGTH + 2 && digits.startsWith("90")) {
            digits = digits.substring(2);
        }
        if (digits.length() == NATIONAL_LENGTH + 1 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits;
    }

    /** Ten digits and no trunk zero — the shape a carrier accepts. */
    public static boolean isValid(String raw) {
        String national = national(raw);
        return national != null
                && national.length() == NATIONAL_LENGTH
                && !national.startsWith("0");
    }

    /** Turkish mobile numbers begin with 5; SMS cannot be delivered to anything else. */
    public static boolean isMobile(String raw) {
        return isValid(raw) && national(raw).startsWith("5");
    }

    /** The domestic written form, {@code 05321112233}, for storing and showing. */
    public static String withTrunkZero(String raw) {
        String national = national(raw);
        return national == null || national.isEmpty() ? national : "0" + national;
    }
}
