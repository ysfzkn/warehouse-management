package com.warehouse.assistant.core.rag;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.warehouse.assistant.core.config.AssistantProperties;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Embedding facade that supports two providers, selectable at runtime from the
 * admin UI:
 * <ul>
 *   <li><b>AZURE</b> — Azure OpenAI. Uses Spring AI's auto-configured
 *       {@link EmbeddingModel} when no separate endpoint is set, otherwise
 *       builds a dedicated {@link AzureOpenAiEmbeddingModel} for a different
 *       Azure resource (KVKK data residency, quota isolation).</li>
 *   <li><b>OPENAI</b> — Vanilla OpenAI API. Builds an {@link OpenAiEmbeddingModel}
 *       pointing to https://api.openai.com (or a custom base URL / Azure-compatible
 *       proxy) using the admin-supplied API key and model id.</li>
 * </ul>
 * The active mode is re-checked on each embedding call; client is rebuilt only
 * when configuration actually changes.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Auto-configured by Spring AI (shared Azure endpoint). May be null if no Azure config. */
    private final EmbeddingModel autoConfiguredModel;
    private final AssistantProperties props;
    private final AssistantRuntimeConfig runtimeConfig;

    /** Main Azure chat endpoint — fallback when separate embedding endpoint is empty. */
    @Value("${spring.ai.azure.openai.endpoint:}")
    private String mainEndpoint;

    @Value("${spring.ai.azure.openai.api-key:}")
    private String mainApiKey;

    @Value("${spring.ai.azure.openai.embedding.options.deployment-name:text-embedding-3-small}")
    private String mainAzureEmbeddingDeployment;

    /** Cached dynamic model. Rebuilt when config changes. */
    private volatile EmbeddingModel customModel;
    private volatile String lastProvider;
    private volatile String lastEndpoint;
    private volatile String lastKey;
    private volatile String lastDeployment;

    public EmbeddingService(@Autowired(required = false) EmbeddingModel autoConfiguredModel,
                            AssistantProperties props,
                            AssistantRuntimeConfig runtimeConfig) {
        this.autoConfiguredModel = autoConfiguredModel;
        this.props = props;
        this.runtimeConfig = runtimeConfig;
    }

    @PostConstruct
    void init() {
        String provider = runtimeConfig.getEmbeddingProvider();
        if ("OPENAI".equals(provider)) {
            log.info("Embedding: vanilla OpenAI provider selected — dynamic client will be built.");
        } else if (props.getEmbedding().hasSeparateEndpoint()) {
            log.info("Embedding: separate Azure endpoint configured — dynamic Azure client will be used.");
        } else if (autoConfiguredModel != null) {
            log.info("Embedding: using shared Azure endpoint (Spring AI auto-config).");
        } else {
            log.warn("No EmbeddingModel available. Vector search and RAG will be disabled.");
        }
    }

    public boolean isAvailable() {
        return resolveModel() != null;
    }

    public Optional<float[]> embed(String text) {
        EmbeddingModel model = resolveModel();
        if (model == null) {
            log.warn("[Embed] skipped — no model available (provider not configured?)");
            return Optional.empty();
        }
        if (text == null || text.isBlank()) {
            log.debug("[Embed] skipped — blank text");
            return Optional.empty();
        }
        long t0 = System.nanoTime();
        try {
            float[] vec = model.embed(text);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (vec == null || vec.length == 0) {
                log.warn("[Embed] empty vector for {} chars ({}ms)", text.length(), ms);
                return Optional.empty();
            }
            log.info("[Embed] ok — {} chars → {} dims ({}ms) preview=\"{}\"",
                    text.length(), vec.length, ms, preview(text, 60));
            return Optional.of(vec);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.error("[Embed] FAILED — {} chars, {}ms: {}", text.length(), ms, e.getMessage());
            return Optional.empty();
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = resolveModel();
        if (model == null || texts == null || texts.isEmpty()) return List.of();
        long t0 = System.nanoTime();
        try {
            List<float[]> out = model.embed(texts);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("[Embed] batch ok — {} texts → {} vectors ({}ms)", texts.size(), out.size(), ms);
            return out;
        } catch (Exception e) {
            log.error("[Embed] batch FAILED — {} texts: {}", texts.size(), e.getMessage());
            return List.of();
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    // ── Dynamic model resolution ──

    private EmbeddingModel resolveModel() {
        String provider = runtimeConfig.getEmbeddingProvider();
        String endpoint = runtimeConfig.getEmbeddingEndpoint();
        String apiKey = runtimeConfig.getEmbeddingApiKey();
        String deployment = runtimeConfig.getEmbeddingDeploymentName();

        // OPENAI → always use custom client
        if ("OPENAI".equals(provider)) {
            if (apiKey == null || apiKey.isBlank() || deployment == null || deployment.isBlank()) {
                return null; // not configured
            }
            if (needsRebuild(provider, endpoint, apiKey, deployment)) {
                rebuildCustomModel(provider, endpoint, apiKey, deployment);
            }
            return customModel;
        }

        // AZURE: fall back to application.properties defaults if UI didn't override
        String effEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : mainEndpoint;
        String effKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : mainApiKey;
        String effDeployment = (deployment != null && !deployment.isBlank()) ? deployment : mainAzureEmbeddingDeployment;

        // If Azure auto-config produced a bean (rare now), and user hasn't overridden anything, use it
        if (autoConfiguredModel != null
                && (endpoint == null || endpoint.isBlank())
                && (apiKey == null || apiKey.isBlank())
                && (deployment == null || deployment.isBlank())) {
            return autoConfiguredModel;
        }

        if (effEndpoint == null || effEndpoint.isBlank() || effKey == null || effKey.isBlank()) {
            return null; // Azure needs both endpoint and key — not configured
        }
        if (needsRebuild(provider, effEndpoint, effKey, effDeployment)) {
            rebuildCustomModel(provider, effEndpoint, effKey, effDeployment);
        }
        return customModel;
    }

    private boolean needsRebuild(String provider, String endpoint, String key, String deployment) {
        return customModel == null
                || !Objects.equals(provider, lastProvider)
                || !Objects.equals(endpoint, lastEndpoint)
                || !Objects.equals(key, lastKey)
                || !Objects.equals(deployment, lastDeployment);
    }

    private synchronized void rebuildCustomModel(String provider, String endpoint, String apiKey, String deployment) {
        try {
            String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : mainApiKey;
            String effectiveDeployment = (deployment != null && !deployment.isBlank())
                    ? deployment : props.getEmbedding().getDeploymentName();

            EmbeddingModel newModel;
            if ("OPENAI".equals(provider)) {
                newModel = buildOpenAiEmbeddingModel(endpoint, effectiveKey, effectiveDeployment);
            } else {
                newModel = buildAzureEmbeddingModel(endpoint, effectiveKey, effectiveDeployment);
            }

            this.customModel = newModel;
            this.lastProvider = provider;
            this.lastEndpoint = endpoint;
            this.lastKey = effectiveKey;
            this.lastDeployment = effectiveDeployment;
            log.info("Embedding: client rebuilt — provider={}, model/deployment={}", provider, effectiveDeployment);
        } catch (Exception e) {
            log.error("Failed to build embedding client ({}): {}", provider, e.getMessage());
            this.customModel = null;
        }
    }

    private EmbeddingModel buildAzureEmbeddingModel(String endpoint, String apiKey, String deployment) {
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();
        AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(deployment)
                .build();
        return new AzureOpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
    }

    private EmbeddingModel buildOpenAiEmbeddingModel(String endpoint, String apiKey, String model) {
        // endpoint empty → Spring AI defaults to https://api.openai.com
        String baseUrl = (endpoint != null && !endpoint.isBlank()) ? endpoint : "https://api.openai.com";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
