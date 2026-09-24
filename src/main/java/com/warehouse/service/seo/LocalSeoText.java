package com.warehouse.service.seo;

import com.warehouse.util.Locales;
import org.jsoup.Jsoup;

/**
 * City-aware wording shared by every server-rendered page.
 *
 * <p>Mirrors {@code frontend/src/utils/seo.js}. The server and the SPA must produce the
 * same title for the same URL: Google sees the server's first and the SPA's after
 * rendering, and a page whose title changes between the two is treated as unstable.</p>
 */
final class LocalSeoText {

    private static final String BACK_VOWELS = "aıou";
    private static final String ALL_VOWELS = "aıoueiöü";
    private static final String VOICELESS_CONSONANTS = "çfhkpsşt";

    private LocalSeoText() {}

    /** "Profilo Buzdolabı" → "Niğde Profilo Buzdolabı"; unchanged if the city is already there. */
    static String withCity(String city, String text) {
        String base = text == null ? "" : text.trim();
        if (city.isEmpty() || base.isEmpty()
                || fold(base).contains(fold(city))) {
            return base;
        }
        return city + " " + base;
    }

    /**
     * "Buzdolabı" + "Regal" → "Buzdolabı Regal", but "Regal 140 Lt Buzdolabı" stays as is:
     * repeating a brand the name already carries only lengthens a title Google cuts at ~60 chars.
     */
    static String withBrand(String name, String brand) {
        String base = name == null ? "" : name.trim();
        if (brand == null || brand.isBlank()
                || fold(base).contains(fold(brand))) {
            return base;
        }
        return base + " " + brand.trim();
    }

    /**
     * Turkish locative of a proper noun: Niğde'de, Ankara'da, Sivas'ta.
     * The vowel follows the last vowel of the word, the consonant hardens after ç f h k p s ş t.
     */
    static String locative(String place) {
        String lower = place.toLowerCase(Locales.TR);
        boolean back = true;
        for (int i = lower.length() - 1; i >= 0; i--) {
            char c = lower.charAt(i);
            if (ALL_VOWELS.indexOf(c) >= 0) {
                back = BACK_VOWELS.indexOf(c) >= 0;
                break;
            }
        }
        boolean hard = !lower.isEmpty() && VOICELESS_CONSONANTS.indexOf(lower.charAt(lower.length() - 1)) >= 0;
        return place + "'" + (hard ? 't' : 'd') + (back ? 'a' : 'e');
    }

    /** Rich-text editor output to plain text, cut at a word boundary. */
    static String plainText(String html, int maxLength) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(html).text().trim();
        if (text.length() <= maxLength) {
            return text;
        }
        int cut = text.lastIndexOf(' ', maxLength);
        return text.substring(0, cut > 0 ? cut : maxLength).trim() + "…";
    }

    /**
     * Case-insensitive form for "is it already in the text" checks. Turkish lower-casing turns
     * "SIMFER" into "sımfer" and "Simfer" into "simfer", so upper-case product names never
     * matched; dotted and dotless i are treated as the same letter.
     */
    static String fold(String text) {
        return text.trim().toLowerCase(Locales.TR).replace('ı', 'i');
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
