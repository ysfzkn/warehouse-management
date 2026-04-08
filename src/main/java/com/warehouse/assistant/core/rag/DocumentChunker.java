package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sliding-window chunker for documents before embedding.
 * <p>
 * We avoid a true BPE tokenizer (would pull another dependency) and use a
 * whitespace-word approximation: one token ≈ one word. This gives the LLM
 * roughly the right-sized chunks for {@code text-embedding-3-small} (which
 * has an 8192-token context, so 500-word chunks leave plenty of headroom).
 * <p>
 * The chunker tries to end each window at a Turkish sentence boundary
 * (. ! ? or newline) so chunks read naturally, falling back to a hard cut
 * at the word limit if no boundary is within the last 20% of the window.
 */
@Component
public class DocumentChunker {

    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern SENTENCE_END = Pattern.compile(".*[.!?]\\s*$", Pattern.DOTALL);

    private final AssistantProperties props;

    public DocumentChunker(AssistantProperties props) {
        this.props = props;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();
        int size = Math.max(50, props.getRag().getChunkSizeTokens());
        int overlap = Math.max(0, Math.min(size / 2, props.getRag().getChunkOverlapTokens()));
        return chunk(text, size, overlap);
    }

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
