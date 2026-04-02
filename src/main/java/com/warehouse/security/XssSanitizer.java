package com.warehouse.security;

import java.util.regex.Pattern;

/**
 * XSS sanitizer that removes dangerous HTML/script content
 * WITHOUT breaking URLs, embeds, and normal text.
 *
 * Previous version was over-aggressive: replacing "/" with "&#x2F;"
 * broke all URLs (https://...), Maps embeds, social media links, etc.
 *
 * This version:
 * - Strips <script> tags and their content
 * - Strips event handlers (onclick, onerror, onload, etc.)
 * - Strips javascript: protocol
 * - Preserves URLs, special characters, embeds
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    // Patterns for dangerous content
    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_OPEN = Pattern.compile(
            "<\\s*script[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PROTOCOL = Pattern.compile(
            "javascript\\s*:", Pattern.CASE_INSENSITIVE);

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String result = input;

        // Remove <script>...</script> blocks
        result = SCRIPT_TAG.matcher(result).replaceAll("");

        // Remove any remaining <script> open tags
        result = SCRIPT_OPEN.matcher(result).replaceAll("");

        // Remove event handlers (onclick="...", onerror="...", etc.)
        result = EVENT_HANDLER.matcher(result).replaceAll("");

        // Remove javascript: protocol
        result = JAVASCRIPT_PROTOCOL.matcher(result).replaceAll("");

        return result.trim();
    }
}
