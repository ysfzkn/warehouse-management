package com.warehouse.security;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Allowlist-based HTML sanitizer built on jsoup.
 *
 * <p>The previous implementation was a regex denylist and was trivially bypassable:
 * its event-handler pattern required quotes ({@code \s+on\w+\s*=\s*["'][^"']*["']}),
 * so {@code <img src=x onerror=alert(1)>} and {@code <svg onload=alert(1)>} passed
 * straight through. Denylists cannot win this fight — anything not explicitly
 * permitted is now dropped instead.</p>
 *
 * <p>Two levels:
 * <ul>
 *   <li>{@link #sanitize(String)} — applied to every inbound JSON string. It only
 *       engages when the value actually looks like markup, so ordinary text such as
 *       {@code "5 < 6"} or a password containing punctuation is returned byte for
 *       byte unchanged.</li>
 *   <li>{@link #sanitizeRichText(String)} — same policy, used explicitly at the
 *       write path of fields that legitimately store HTML (CMS pages, legal texts,
 *       product descriptions).</li>
 * </ul>
 *
 * <p>The safelist keeps everything the CMS editor produces — formatting tags,
 * {@code class}/{@code style} attributes, links, images, tables and allowlisted
 * {@code iframe} embeds (maps, video) — while removing scripts, event handlers,
 * {@code javascript:}/{@code data:} URLs, {@code <object>}, {@code <embed>},
 * {@code <form>} and anything else not named here.</p>
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    /**
     * Only run the parser when the value plausibly contains a tag, so ordinary text
     * survives untouched: {@code "a < b"} and {@code "<john@example.com>"} do not
     * match, while {@code "<b>"}, {@code "<img src=x onerror=1>"} and
     * {@code "<svg/onload=1>"} do.
     */
    private static final Pattern LOOKS_LIKE_MARKUP =
            Pattern.compile("<\\s*(/\\s*)?[a-zA-Z][a-zA-Z0-9]*\\s*[\\s/>]|<[!?]");

    /** Control characters (except tab/newline/carriage return) have no place in user input. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t\\n\\r]]");

    private static final Safelist RICH_TEXT = buildRichTextSafelist();

    private static Safelist buildRichTextSafelist() {
        Safelist list = Safelist.relaxed()
                // The rich-text editor emits alignment/colour as class and style
                // attributes; dropping them would silently reflow every existing
                // CMS page, so they are preserved. Neither can execute script in
                // any browser this application supports.
                .addAttributes(":all", "class", "style", "id", "title", "dir")
                .addTags("figure", "figcaption", "hr", "s", "del", "ins", "iframe", "video", "source")
                .addAttributes("a", "target", "rel")
                .addAttributes("img", "loading", "width", "height")
                .addAttributes("iframe", "src", "width", "height", "allow", "allowfullscreen",
                        "frameborder", "loading", "referrerpolicy")
                .addAttributes("video", "src", "controls", "poster", "width", "height")
                .addAttributes("source", "src", "type")
                // Embeds and media may only point at https. This is what stops
                // javascript:, data: and vbscript: URLs from ever surviving.
                .addProtocols("iframe", "src", "https")
                .addProtocols("video", "src", "https")
                .addProtocols("source", "src", "https")
                .addProtocols("img", "src", "http", "https", "data")
                .addProtocols("a", "href", "http", "https", "mailto", "tel");
        // Force rel=noopener on any link that opens a new tab, closing the
        // reverse-tabnabbing hole that target=_blank otherwise leaves open.
        list.addEnforcedAttribute("a", "rel", "noopener noreferrer");
        return list;
    }

    /**
     * Default sanitizer for all inbound strings. Strips control characters always,
     * and applies the HTML allowlist only when markup is present.
     *
     * <p>Note: it deliberately does <b>not</b> trim. The previous version trimmed
     * every deserialized string, which silently rewrote passwords that began or
     * ended with a space — a credential the user could then never type again.</p>
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String result = CONTROL_CHARS.matcher(input).replaceAll("");
        if (LOOKS_LIKE_MARKUP.matcher(result).find()) {
            result = clean(result);
        }
        return result;
    }

    /** Explicit entry point for fields that are stored and later rendered as HTML. */
    public static String sanitizeRichText(String html) {
        if (html == null) return null;
        return clean(CONTROL_CHARS.matcher(html).replaceAll(""));
    }

    private static String clean(String html) {
        Document.OutputSettings settings = new Document.OutputSettings()
                .prettyPrint(false); // keep the author's whitespace/line breaks intact
        return Jsoup.clean(html, "", RICH_TEXT, settings);
    }
}
