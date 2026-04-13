package com.warehouse.assistant.core.rag;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.warehouse.assistant.core.config.AssistantProperties;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import org.springframework.ai.document.MetadataMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * Embedding facade that supports two modes:
 * <ol>
 *   <li><b>Shared endpoint</b> (default): Uses the auto-configured
 *       {@link EmbeddingModel} from Spring AI, which shares the main Azure
 *       OpenAI endpoint with the chat model.</li>
 *   <li><b>Separate endpoint</b>: When {@code assistant.embedding.endpoint} is
 *       set (via env var or admin UI), creates a dedicated Azure OpenAI client
 *       pointing to a different resource/region. Useful for KVKK data
 *       residency or quota isolation.</li>
 * </ol>
 * The active mode is determined at boot time from properties and can be
 * changed at runtime via the admin "AI Ayarları" page — changes take effect
 * on the next embedding call.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Auto-configured by Spring AI (shared endpoint). May be null if no Azure config. */
    private final EmbeddingModel autoConfiguredModel;
    private final AssistantProperties props;
    private final AssistantRuntimeConfig runtimeConfig;

    /** Main chat endpoint — used as fallback when embedding endpoint is empty. */
    @Value("${spring.ai.azure.openai.endpoint:}")
    private String mainEndpoint;

    @Value("${spring.ai.azure.openai.api-key:}")
    private String mainApiKey;

    /** Custom client for separate endpoint. Lazily built, rebuilt when config changes. */
    private volatile EmbeddingModel customModel;
    private volatile String lastCustomEndpoint;
    private volatile String lastCustomKey;
    private volatile String lastCustomDeployment;

    public EmbeddingService(@Autowired(required = false) EmbeddingModel autoConfiguredModel,
                            AssistantProperties props,
                            AssistantRuntimeConfig runtimeConfig) {
        this.autoConfiguredModel = autoConfiguredModel;
        this.props = props;
        this.runtimeConfig = runtimeConfig;
    }

    @PostConstruct
    void init() {
        if (props.getEmbedding().hasSeparateEndpoint()) {
            log.info("Embedding: separate Azure endpoint configured ({}). Custom client will be used.",
                    maskEndpoint(props.getEmbedding().getEndpoint()));
            rebuildCustomModel(props.getEmbedding().getEndpoint(),
                    props.getEmbedding().getApiKey(),
                    props.getEmbedding().getDeploymentName());
        } else if (autoConfiguredModel != null) {
            log.info("Embedding: using shared Azure endpoint (auto-configured).");
        } else {
            log.warn("No EmbeddingModel available. Vector search and RAG will be disabled. "
                    + "Set spring.ai.azure.openai.embedding.options.deployment-name to enable.");
        }
    }

    public boolean isAvailable() {
        return resolveModel() != null;
    }

    public Optional<float[]> embed(String text) {
        EmbeddingModel model = resolveModel();
        if (model == null || text == null || text.isBlank()) return Optional.empty();
        try {
            float[] vec = model.embed(text);
            if (vec == null || vec.length == 0) return Optional.empty();
            return Optional.of(vec);
        } catch (Exception e) {
            log.error("Embedding call failed for text of length {}: {}", text.length(), e.getMessage());
            return Optional.empty();
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = resolveModel();
        if (model == null || texts == null || texts.isEmpty()) return List.of();
        try {
            return model.embed(texts);
        } catch (Exception e) {
            log.error("Batch embedding failed for {} texts: {}", texts.size(), e.getMessage());
            return List.of();
        }
    }

    // ── Runtime config check ──

    private EmbeddingModel resolveModel() {
        // Check if admin changed the embedding endpoint via UI
        String cfgEndpoint = runtimeConfig.getEmbeddingEndpoint();
        String cfgKey = runtimeConfig.getEmbeddingApiKey();
        String cfgDeployment = runtimeConfig.getEmbeddingDeploymentName();

        if (cfgEndpoint != null && !cfgEndpoint.isBlank()) {
            // Separate endpoint configured — use or rebuild custom model
            if (needsRebuild(cfgEndpoint, cfgKey, cfgDeployment)) {
                rebuildCustomModel(cfgEndpoint, cfgKey, cfgDeployment);
            }
            return customModel;
        }
        // Default: auto-configured shared model
        return autoConfiguredModel;
    }

    private boolean needsRebuild(String endpoint, String key, String deployment) {
        return customModel == null
                || !endpoint.equals(lastCustomEndpoint)
                || !String.valueOf(key).equals(String.valueOf(lastCustomKey))
                || !String.valueOf(deployment).equals(String.valueOf(lastCustomDeployment));
    }

    private synchronized void rebuildCustomModel(String endpoint, String apiKey, String deploymentName) {
        try {
            String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : mainApiKey;
            String effectiveDeployment = (deploymentName != null && !deploymentName.isBlank())
                    ? deploymentName : props.getEmbedding().getDeploymentName();

            OpenAIClient client = new OpenAIClientBuilder()
                    .endpoint(endpoint)
                    .credential(new AzureKeyCredential(effectiveKey))
                    .buildClient();

            AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                    .deploymentName(effectiveDeployment)
                    .build();

            this.customModel = new AzureOpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
            this.lastCustomEndpoint = endpoint;
            this.lastCustomKey = effectiveKey;
            this.lastCustomDeployment = effectiveDeployment;
            log.info("Embedding: custom client rebuilt for endpoint={}, deployment={}",
                    maskEndpoint(endpoint), effectiveDeployment);
        } catch (Exception e) {
            log.error("Failed to build custom embedding client for {}: {}", endpoint, e.getMessage());
            this.customModel = null;
        }
    }

    private String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() < 20) return "***";
        return endpoint.substring(0, 15) + "***";
    }
}
