package com.warehouse.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turkish-aware text matching for customer names.
 *
 * <p>Two things make a naive {@code contains()} useless here. First, the same name is written
 * with and without Turkish letters ("Ayşe Yılmaz" / "AYSE YILMAZ"), and {@code toLowerCase()}
 * on a dotted capital I mangles it unless the Turkish locale is used. Second, notes are written
 * in running Turkish, where names take suffixes — "Ayşe Yılmaz'a teslim edildi" contains
 * "yilmaza", not "yilmaz" — so tokens are compared by prefix, not equality.</p>
 *
 * <p>To keep the prefix rule from firing on "Ali" inside "Alican", a multi-word name must match
 * at least two distinct tokens; a single-word name has to match a whole token.</p>
 */
public final class TurkishText {

    /** Tokens shorter than this carry no identifying power (initials, "ve", "bay"…). */
    private static final int MIN_TOKEN = 3;

    /** Words that show up in delivery notes and would otherwise act as a name token. */
    private static final Set<String> STOP_WORDS = Set.of(
        "bay", "bayan", "sayin", "musteri", "musterisi", "teslim", "edildi", "verildi",
        "gonderildi", "iade", "adet", "urun", "siparis", "icin", "adina", "kargo", "not");

    private static final Locale TR = new Locale("tr", "TR");

    private TurkishText() {}

    /**
     * Lower-cases with Turkish rules, folds Turkish letters onto their ASCII counterparts and
     * reduces everything else to single spaces. "Ayşe YILMAZ'ın" → "ayse yilmazin".
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(TR);
        StringBuilder sb = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            switch (c) {
                case 'ı', 'i', 'î' -> sb.append('i');
                case 'ş' -> sb.append('s');
                case 'ğ' -> sb.append('g');
                case 'ü', 'û' -> sb.append('u');
                case 'ö' -> sb.append('o');
                case 'ç' -> sb.append('c');
                case 'â' -> sb.append('a');
                // Anything outside a-z0-9 becomes a separator. This is not just tidiness: the
                // same folding is done in SQL by wm_normalize_search (V87), which reduces to
                // exactly [a-z0-9]. Keeping other letters here — "é", "ñ" — would make a row
                // written by the application disagree with a row backfilled by the migration,
                // and a search could then never match one of them.
                default -> sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : ' ');
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Joins the given parts and normalises them into the value stored in a {@code *_search}
     * column. Returns null for an all-blank input so the column stays NULL rather than empty,
     * which keeps "no data" and "normalised to nothing" distinguishable.
     */
    public static String normalizeForSearch(String... parts) {
        if (parts == null || parts.length == 0) return null;
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(part);
        }
        // Normalising turns "0532 111 22 33" into separate groups, so a search for the compact
        // form would miss. Both spellings go into the column.
        for (String part : parts) {
            if (part == null) continue;
            String digits = part.replaceAll("\\D", "");
            if (digits.length() >= 7) joined.append(' ').append(digits);
        }
        String normalized = normalize(joined.toString());
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Wraps a user's query into a LIKE pattern for a {@code *_search} column. Returns null when
     * there is nothing to search on, which the queries read as "no filter".
     */
    public static String searchPattern(String query) {
        String normalized = normalize(query);
        return normalized.isEmpty() ? null : "%" + normalized + "%";
    }

    /** Identifying tokens of a name or a free-text note, in order, without duplicates. */
    public static List<String> tokens(String raw) {
        List<String> result = new ArrayList<>();
        String normalized = normalize(raw);
        if (normalized.isEmpty()) return result;
        for (String token : normalized.split(" ")) {
            if (token.length() < MIN_TOKEN) continue;
            if (STOP_WORDS.contains(token)) continue;
            if (!result.contains(token)) result.add(token);
        }
        return result;
    }

    /**
     * True when {@code name} identifies someone inside {@code text}. {@code text} may be another
     * name or a whole sentence — the note field is matched exactly the same way.
     */
    public static boolean nameOccursIn(String name, String text) {
        return matchedTokens(name, text).size() >= requiredTokenCount(name);
    }

    /** The name tokens that were found, so the UI can show what actually matched. */
    public static List<String> matchedTokens(String name, String text) {
        List<String> nameTokens = tokens(name);
        List<String> textTokens = tokens(text);
        List<String> matched = new ArrayList<>();
        if (nameTokens.isEmpty() || textTokens.isEmpty()) return matched;

        // Each text token can satisfy at most one name token, otherwise a single word would
        // "prove" both the first and the last name.
        Set<String> consumed = new LinkedHashSet<>();
        for (String nameToken : nameTokens) {
            for (String textToken : textTokens) {
                if (consumed.contains(textToken)) continue;
                if (textToken.startsWith(nameToken)) {
                    consumed.add(textToken);
                    matched.add(nameToken);
                    break;
                }
            }
        }
        return matched;
    }

    /** One-word names must match whole; longer names need two tokens to count as a hit. */
    public static int requiredTokenCount(String name) {
        return tokens(name).size() >= 2 ? 2 : 1;
    }

    /** Last 10 digits, so "0532 111 22 33", "+90 532 111 22 33" and "5321112233" all compare equal. */
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    public static boolean phonesMatch(String a, String b) {
        String left = normalizePhone(a);
        String right = normalizePhone(b);
        return left.length() == 10 && left.equals(right);
    }

    /**
     * "  ayşe   YILMAZ " → "Ayşe Yılmaz". The client normalises names on blur; doing it again on
     * the way in means a record written by an older client, an import or the API directly still
     * lands in the same shape, so the same person reads identically on every screen.
     *
     * <p>The Turkish catch: the capital of "i" is "İ" and of "ı" is "I", which a plain
     * {@code toUpperCase()} gets wrong.</p>
     */
    public static String toTitleCase(String raw) {
        if (raw == null) return null;
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return "";
        StringBuilder out = new StringBuilder(collapsed.length());
        boolean startOfWord = true;
        for (char c : collapsed.toCharArray()) {
            if (c == ' ' || c == '-' || c == '\'' || c == '’') {
                out.append(c);
                startOfWord = true;
                continue;
            }
            String single = String.valueOf(c);
            if (startOfWord) {
                out.append(c == 'i' ? 'İ' : c == 'ı' ? 'I' : single.toUpperCase(TR).charAt(0));
                startOfWord = false;
            } else {
                out.append(single.toLowerCase(TR));
            }
        }
        return out.toString();
    }

    /**
     * Product-name casing. Only lower-case words are touched — anything already carrying a
     * capital is left exactly as typed, which protects model codes ("BD3086W3VN"), capacities
     * ("9KG", "A+++"), brands ("LG", "PROFILO") and deliberate mixed case ("iPhone").
     *
     * <p>The all-caps rule is not caution for its own sake: in ALL-CAPS Turkish an "I" can mean
     * either "ı" or "i", so "PROFILO" would come out "Profılo" while "BUZDOLABI" needs exactly
     * the opposite. Leaving the operator's capitals alone beats guessing.</p>
     */
    public static String toProductNameCase(String raw) {
        if (raw == null) return null;
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return "";
        StringBuilder out = new StringBuilder(collapsed.length());
        for (String word : collapsed.split(" ")) {
            if (out.length() > 0) out.append(' ');
            out.append(keepAsTyped(word) ? word : toTitleCase(word));
        }
        return out.toString();
    }

    /**
     * Stock notes, which are sometimes just a customer name ("ahmet yılmaz") and sometimes a
     * whole sentence. A short, purely alphabetic value is treated as a name and title cased;
     * anything longer keeps the operator's wording and only gains a capital first letter.
     */
    public static String toNoteCase(String raw) {
        if (raw == null) return null;
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return "";

        String[] words = collapsed.split(" ");
        boolean looksLikeName = words.length <= 4;
        if (looksLikeName) {
            for (String word : words) {
                for (char c : word.toCharArray()) {
                    if (!Character.isLetter(c) && c != '\'' && c != '’' && c != '-') {
                        looksLikeName = false;
                        break;
                    }
                }
                if (!looksLikeName) break;
            }
        }
        if (looksLikeName) return toTitleCase(collapsed);

        char first = collapsed.charAt(0);
        String head = first == 'i' ? "İ" : first == 'ı' ? "I"
            : String.valueOf(first).toUpperCase(TR);
        return head + collapsed.substring(1);
    }

    private static boolean keepAsTyped(String word) {
        for (char c : word.toCharArray()) {
            if (Character.isDigit(c)) return true;
            if (Character.isUpperCase(c)) return true;
        }
        return false;
    }

    /** True when the value has enough substance to search on at all. */
    public static boolean isSearchable(String name) {
        return !tokens(name).isEmpty();
    }
}
