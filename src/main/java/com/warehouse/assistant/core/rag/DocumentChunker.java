package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.core.config.AssistantProperties;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-mode chunker for document embedding:
 *
 * <ol>
 *   <li><b>Structural (default for legal/policy docs):</b> splits on numbered
 *       headers (<code>2.2.2</code>, <code>BÖLÜM N</code>, <code>MADDE N</code>,
 *       <code>EK N</code>) so each chunk is exactly one semantic section.
 *       Each section is then word-window-trimmed so no chunk exceeds the
 *       configured word limit. The parent header is prepended to every chunk
 *       for retrieval context ("2.2.2 Özel Nitelikli Kişisel Verilerin İşlenmesi —
 *       Kanun'un 6. maddesinde …").</li>
 *
 *   <li><b>Sliding-window (fallback):</b> used when no clear structure is
 *       detected (the heuristic fires fewer than 3 headers per document).
 *       Splits on word count with sentence-boundary snap in the last 20%
 *       of each window.</li>
 * </ol>
 *
 * The structural path dramatically improves retrieval for Turkish KVKK /
 * policy / regulation documents because each chunk is a single topic rather
 * than a multi-topic average.
 */
@Component
public class DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunker.class);
    private static final Pattern WS = Pattern.compile("\\s+");

    /**
     * Turkish legal/policy header markers. Must match at line start (we anchor
     * with {@code (?m)^} when scanning). Captured groups: full header line.
     * Examples that match:
     * <ul>
     *   <li>{@code 2.2.2 Özel Nitelikli Kişisel Verilerin İşlenmesi}</li>
     *   <li>{@code 1. BÖLÜM TANIMLAR}</li>
     *   <li>{@code BÖLÜM 5}</li>
     *   <li>{@code MADDE 6 — İşleme Şartları}</li>
     *   <li>{@code EK 1 – Tanımlar}</li>
     * </ul>
     */
    private static final Pattern HEADER = Pattern.compile(
            "(?m)^\\s*(" +
                    // numbered like 1., 2.1, 2.2.2, 2.2.2.1 followed by title
                    "\\d+(?:\\.\\d+){0,4}\\.?\\s+[^\\n]{3,200}" +
                    "|" +
                    // BÖLÜM N — title
                    "(?:\\d+\\.\\s*)?BÖLÜM\\s+\\d+[^\\n]{0,200}" +
                    "|" +
                    // MADDE N — title
                    "MADDE\\s+\\d+[^\\n]{0,200}" +
                    "|" +
                    // EK N - title
                    "EK\\s+\\d+[^\\n]{0,200}" +
                    ")\\s*$");

    private final AssistantProperties props;
    private final AssistantRuntimeConfig runtimeConfig;

    public DocumentChunker(AssistantProperties props, AssistantRuntimeConfig runtimeConfig) {
        this.props = props;
        this.runtimeConfig = runtimeConfig;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            log.debug("[Chunker] blank input, returning empty");
            return List.of();
        }
        int size = Math.max(50, runtimeConfig.getChunkSizeTokens());
        int overlap = Math.max(0, Math.min(size / 2, runtimeConfig.getChunkOverlapTokens()));

        // Try structural first. If it produces few sections (< 3), fall back.
        List<String> structural = chunkStructural(text, size);
        if (structural != null && structural.size() >= 3) {
            log.info("[Chunker] structural — {} chars → {} chunks (max {} words/chunk)",
                    text.length(), structural.size(), size);
            return structural;
        }

        List<String> sliding = chunk(text, size, overlap);
        log.info("[Chunker] sliding-window — {} chars → {} chunks (window={}, overlap={} words)",
                text.length(), sliding.size(), size, overlap);
        return sliding;
    }

    /**
     * Structural chunker: splits on numbered / named headers, then trims each
     * section to {@code maxWords}. Returns {@code null} if no headers were
     * detected (signals the caller to fall back to sliding-window).
     */
    List<String> chunkStructural(String text, int maxWords) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        Matcher m = HEADER.matcher(normalized);

        List<int[]> marks = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        while (m.find()) {
            marks.add(new int[]{ m.start(), m.end() });
            headers.add(m.group(1).trim());
        }
        if (marks.size() < 3) return null; // not enough structure; use fallback

        List<String> out = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            int sectionStart = marks.get(i)[0];
            int sectionEnd = (i + 1 < marks.size()) ? marks.get(i + 1)[0] : normalized.length();
            String header = headers.get(i);
            String body = normalized.substring(sectionStart, sectionEnd).trim();

            // Drop sections that are just the header with no body
            String bodyOnly = body.substring(Math.min(body.length(), header.length())).trim();
            if (bodyOnly.isEmpty() && body.equals(header)) continue;

            // If the section itself is still huge (> maxWords), split it with
            // sliding-window, prefixing every sub-chunk with the header so the
            // embedding is anchored to the topic.
            String[] words = WS.split(body);
            if (words.length <= maxWords) {
                out.add(body);
            } else {
                List<String> pieces = chunk(body, maxWords, Math.min(50, maxWords / 4));
                for (int p = 0; p < pieces.size(); p++) {
                    String piece = pieces.get(p);
                    if (p == 0 || piece.startsWith(header)) {
                        out.add(piece);
                    } else {
                        out.add(header + " — " + piece);
                    }
                }
            }
        }
        return out;
    }

    /** Original sliding-window chunker (sentence-boundary snap). */
    public List<String> chunk(String text, int windowSize, int overlap) {
        String normalized = text.strip().replace("\r\n", "\n");
        String[] words = WS.split(normalized);
        if (words.length == 0) return List.of();

        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + windowSize, words.length);
            // Try to snap to a sentence boundary in the last 20% of the window.
            int snapStart = Math.max(start + (windowSize * 4 / 5), start + 1);
            int snap = -1;
            for (int i = end; i > snapStart; i--) {
                String w = words[i - 1];
                if (!w.isEmpty() && (w.endsWith(".") || w.endsWith("!") || w.endsWith("?"))) {
                    snap = i;
                    break;
                }
            }
            if (snap > 0) end = snap;

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) sb.append(' ');
                sb.append(words[i]);
            }
            String chunk = sb.toString().trim();
            if (!chunk.isEmpty()) out.add(chunk);

            if (end >= words.length) break;
            start = Math.max(end - overlap, start + 1);
        }
        return out;
    }
}
