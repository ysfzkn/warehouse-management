package com.warehouse.assistant.core.rag;

import com.warehouse.assistant.core.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around Spring AI's auto-configured {@link EmbeddingModel}.
 * Two reasons this exists as its own layer:
 * <ol>
 *   <li>The rest of the application shouldn't depend directly on Spring AI
 *       classes — makes it trivial to swap embedding providers in the future.</li>
 *   <li>{@link EmbeddingModel} is {@code @Autowired(required = false)} here so
 *       the app can still boot if the Azure embedding deployment is missing
 *       (useful in local dev). In that case vector search silently returns
 *       empty results — logged as a warning, not an error.</li>
 * </ol>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final AssistantProperties props;

    public EmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel,
                            AssistantProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
        if (embeddingModel == null) {
            log.warn("No EmbeddingModel bean available. Vector search and RAG ingestion will be disabled. "
                    + "Set spring.ai.azure.openai.embedding.options.deployment-name to enable.");
        }
    }

    /** @return true when the Azure embedding deployment is wired and ready. */
    public boolean isAvailable() {
        return embeddingModel != null;
    }

    /** Compute one embedding. Returns empty if the embedding model is not available. */
    public Optional<float[]> embed(String text) {
        if (embeddingModel == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            float[] vec = embeddingModel.embed(text);
            if (vec == null || vec.length == 0) {
                return Optional.empty();
            }
            if (vec.length != props.getRag().getEmbeddingDimensions()) {
                log.warn("Embedding dimension mismatch: expected={}, actual={}. Check embedding model deployment.",
                        props.getRag().getEmbeddingDimensions(), vec.length);
            }
            return Optional.of(vec);
        } catch (Exception e) {
            log.error("Embedding call failed for text of length {}: {}", text.length(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Batch embed multiple texts — preserves input order. Empty on failure. */
    public List<float[]> embedAll(List<String> texts) {
        if (embeddingModel == null || texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            return embeddingModel.embed(texts);
        } catch (Exception e) {
            log.error("Batch embedding call failed for {} texts: {}", texts.size(), e.getMessage());
            return List.of();
        }
    }
}
