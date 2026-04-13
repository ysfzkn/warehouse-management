package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        props.getRag().setChunkSizeTokens(10);
        props.getRag().setChunkOverlapTokens(2);
        chunker = new DocumentChunker(props);
    }

    @Test
    void emptyInput() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    void shortTextSingleChunk() {
        List<String> chunks = chunker.chunk("Merhaba dünya");
        assertEquals(1, chunks.size());
        assertEquals("Merhaba dünya", chunks.get(0));
    }

    @Test
    void chunksWithOverlap() {
        // 20 words → multiple chunks of ~10 with overlap 2
        // Use the direct 3-arg method to bypass the min-50 guard in chunk(text).
        String text = "bir iki üç dört beş altı yedi sekiz dokuz on "
                + "onbir oniki onüç ondört onbeş onaltı onyedi onsekiz ondokuz yirmi";
        List<String> chunks = chunker.chunk(text, 10, 2);
        assertTrue(chunks.size() >= 2, "Expected multiple chunks, got " + chunks.size());

        // Overlap: last 2 words of chunk 1 should appear at start of chunk 2
        String chunk1 = chunks.get(0);
        String chunk2 = chunks.get(1);
        String[] words1 = chunk1.split("\\s+");
        String[] words2 = chunk2.split("\\s+");
        assertEquals(words1[words1.length - 2], words2[0]);
        assertEquals(words1[words1.length - 1], words2[1]);
    }

    @Test
    void sentenceBoundarySnapping() {
        // Sentence boundary snapping activates only within the last 20% of the
        // window. This text has a sentence end near the window edge so it snaps.
        // 10-word window; sentence boundary at word index 9 (inside last 20%).
        String text = "Bir iki üç dört beş altı yedi sekiz dokuz biter. Onbir oniki onüç ondört.";
        List<String> chunks = chunker.chunk(text, 10, 2);
        assertTrue(chunks.size() >= 2, "Expected ≥2 chunks, got " + chunks.size());
        assertTrue(chunks.get(0).endsWith("biter."),
                "First chunk should snap to sentence boundary 'biter.', got: " + chunks.get(0));
    }

    @Test
    void largeWindowFitsAllContent() {
        AssistantProperties big = new AssistantProperties();
        big.getRag().setChunkSizeTokens(1000);
        big.getRag().setChunkOverlapTokens(50);
        DocumentChunker bigChunker = new DocumentChunker(big);
        String text = "Kısa bir metin.";
        List<String> chunks = bigChunker.chunk(text);
        assertEquals(1, chunks.size());
    }
}
