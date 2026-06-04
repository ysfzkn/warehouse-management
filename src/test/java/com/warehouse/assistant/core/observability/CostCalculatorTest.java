package com.warehouse.assistant.core.observability;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CostCalculatorTest {

    private CostCalculator calc;

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        // Defaults: prompt=0.0025/1K, completion=0.010/1K, embedding=0.00002/1K
        calc = new CostCalculator(props);
    }

    @Test
    void zeroCost() {
        assertEquals(new BigDecimal("0.000000"), calc.chatCost(0, 0));
        assertEquals(new BigDecimal("0.000000"), calc.embeddingCost(0));
    }

    @Test
    void negativeSafe() {
        // Negative tokens should be treated as 0.
        assertEquals(new BigDecimal("0.000000"), calc.chatCost(-100, -200));
    }

    @Test
    void knownChatCost() {
        // 1000 prompt tokens * 0.0025/1K = 0.0025
        // 500 completion tokens * 0.010/1K = 0.005
        // Total = 0.0075
        BigDecimal cost = calc.chatCost(1000, 500);
        assertEquals(new BigDecimal("0.007500"), cost);
    }

    @Test
    void knownEmbeddingCost() {
        // 1_000_000 tokens * 0.00002/1K = 0.020
        BigDecimal cost = calc.embeddingCost(1_000_000);
        assertEquals(0, new BigDecimal("0.020000").compareTo(cost),
                "Expected 0.020000 but got " + cost);
    }

    @Test
    void scalePrecision() {
        // All costs should have exactly 6 decimal places.
        BigDecimal cost = calc.chatCost(1, 1);
        assertEquals(6, cost.scale());
    }
}
