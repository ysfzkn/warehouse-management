package com.warehouse.assistant.core.observability;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Converts raw token counts into USD cost using the configured Azure OpenAI
 * price table. All arithmetic is done in {@link BigDecimal} so the dashboard
 * can render fractional-cent values without floating-point drift.
 */
@Component
public class CostCalculator {

    private static final BigDecimal PER_1K = new BigDecimal("1000");
    private static final MathContext MC = new MathContext(12);
    private static final int SCALE = 6;

    private final AssistantProperties props;

    public CostCalculator(AssistantProperties props) {
        this.props = props;
    }

    /** Cost for a single chat turn (one prompt + one completion). */
    public BigDecimal chatCost(long promptTokens, long completionTokens) {
        BigDecimal prompt = BigDecimal.valueOf(Math.max(0, promptTokens))
                .multiply(props.getCost().getChat().getPromptPer1kUsd(), MC)
                .divide(PER_1K, SCALE, RoundingMode.HALF_UP);
        BigDecimal completion = BigDecimal.valueOf(Math.max(0, completionTokens))
                .multiply(props.getCost().getChat().getCompletionPer1kUsd(), MC)
                .divide(PER_1K, SCALE, RoundingMode.HALF_UP);
        return prompt.add(completion).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Cost for an embedding call (only prompt-side tokens are billed). */
    public BigDecimal embeddingCost(long tokens) {
        return BigDecimal.valueOf(Math.max(0, tokens))
                .multiply(props.getCost().getEmbedding().getPer1kUsd(), MC)
                .divide(PER_1K, SCALE, RoundingMode.HALF_UP);
    }
}
