package com.warehouse.assistant.core.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the pgvector literal formatter (the only VectorSearchService method
 * that doesn't need a live database).
 */
class VectorSearchServiceTest {

    @Test
    void literalFormatBasic() {
        float[] v = {1.0f, 2.5f, -3.14f};
        String literal = VectorSearchService.toPgVectorLiteral(v);
        assertEquals("[1.0,2.5,-3.14]", literal);
    }

    @Test
    void literalFormatSingleElement() {
        float[] v = {0.0f};
        assertEquals("[0.0]", VectorSearchService.toPgVectorLiteral(v));
    }

    @Test
    void literalFormatEmpty() {
        float[] v = {};
        assertEquals("[]", VectorSearchService.toPgVectorLiteral(v));
    }

    @Test
    void literalFormatHighDimensional() {
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) v[i] = i * 0.001f;
        String literal = VectorSearchService.toPgVectorLiteral(v);
        assertTrue(literal.startsWith("["));
        assertTrue(literal.endsWith("]"));
        // Should contain 1536 comma-separated values → 1535 commas
        long commaCount = literal.chars().filter(c -> c == ',').count();
        assertEquals(1535, commaCount);
    }
}
