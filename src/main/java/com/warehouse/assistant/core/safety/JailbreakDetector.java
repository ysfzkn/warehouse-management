package com.warehouse.assistant.core.safety;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects known jailbreak / prompt-injection patterns in user messages.
 * <p>
 * <b>Philosophy:</b> Detection does NOT block the message (the prompt-level
 * defense handles that). Instead it logs a security audit event so the admin
 * can monitor attack frequency and refine the system prompt if needed.
 * <p>
 * Patterns cover both Turkish and English since attackers commonly try both.
 * The patterns are intentionally broad — false positives on innocent messages
 * like "bu kuralları bilmiyorum" are acceptable because we never block, only log.
 */
@Component
public class JailbreakDetector {

    private static final List<Pattern> PATTERNS = List.of(
            // Turkish patterns
            ci("yukar[ıi]daki\\s+(talimat|kural|komut|prompt)"),
            ci("(yoksay|unut|g[öo]rmezden\\s+gel|iptal\\s+et|de[ğg]i[şs]tir).*talimat"),
            ci("(talimat|kural|prompt|sistem\\s+mesaj).*?(yoksay|unut|g[öo]rmezden)"),
            ci("sen\\s+art[ıi]k.*de[ğg]ilsin"),
            ci("rol[üu]n[üu]\\s+(de[ğg]i[şs]tir|unut)"),
            ci("(ger[çc]ek|as[ıi]l|gizli)\\s+(talimat|prompt|kural)"),
            ci("(prompt|sistem\\s+mesaj|i[çc]\\s+kural).*?(g[öo]ster|yaz|payla[şs])"),
            ci("developer\\s*mod"),

            // English patterns
            ci("ignore\\s+(all\\s+)?previous"),
            ci("disregard\\s+(all\\s+)?instructions"),
            ci("override\\s+(system|safety|rules)"),
            ci("(system|initial)\\s+prompt"),
            ci("DAN\\s+mode"),
            ci("(pretend|act\\s+as|you\\s+are\\s+now)\\s+"),
            ci("(reveal|show|print|display|output)\\s+(your|the|system)\\s+(prompt|instructions|rules)"),
            ci("(new|different|updated)\\s+instructions"),
            ci("jailbreak|bypass\\s+(filter|safety|rules)")
    );

    /**
     * @return true if the message contains any jailbreak/injection pattern.
     */
    public boolean isJailbreakAttempt(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        for (Pattern p : PATTERNS) {
            if (p.matcher(lower).find()) return true;
        }
        return false;
    }

    /**
     * @return the first matching pattern description, or null if clean.
     */
    public String detectPattern(String message) {
        if (message == null || message.isBlank()) return null;
        String lower = message.toLowerCase(Locale.ROOT);
        for (Pattern p : PATTERNS) {
            if (p.matcher(lower).find()) return p.pattern();
        }
        return null;
    }

    private static Pattern ci(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
