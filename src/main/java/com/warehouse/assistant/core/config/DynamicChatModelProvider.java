package com.warehouse.assistant.core.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Runtime-dynamic chat model provider. Builds the appropriate
 * {@link ChatModel} implementation based on current {@link AssistantRuntimeConfig}:
 * <ul>
 *   <li><b>AZURE</b> — Azure OpenAI (deployment-based). Falls back to Spring AI's
 *       auto-configured shared model when no custom endpoint is set.</li>
 *   <li><b>OPENAI</b> — vanilla OpenAI API (model id). Built on demand using
 *       the admin-supplied key + model id.</li>
 * </ul>
 * The resolved model is cached and rebuilt only when configuration changes,
 * so the per-request overhead is a trivial identity check.
 */
@Service
public class DynamicChatModelProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatModelProvider.class);

    /** Spring AI auto-configured ChatModel (Azure OpenAI from properties). May be null. */
    private final ChatModel autoConfiguredChatModel;
    private final AssistantRuntimeConfig runtimeConfig;

    @Value("${spring.ai.azure.openai.endpoint:}")
    private String defaultAzureEndpoint;

    @Value("${spring.ai.azure.openai.api-key:}")
    private String defaultAzureApiKey;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name:gpt-4o-mini}")
    private String defaultAzureDeployment;

    /** Cached custom model and the config signature that produced it. */
    private volatile ChatModel cachedModel;
    private volatile String cachedSignature;

    public DynamicChatModelProvider(@Autowired(required = false) ChatModel autoConfiguredChatModel,
                                    AssistantRuntimeConfig runtimeConfig) {
        this.autoConfiguredChatModel = autoConfiguredChatModel;
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Returns the currently active ChatModel. Rebuilt only if the underlying
     * configuration has changed since the last call.
     *
     * @return configured ChatModel, or {@code null} when no valid provider configuration
     *         is available (caller must handle gracefully and show "AI not configured").
     */
    public ChatModel current() {
        String provider = runtimeConfig.getChatProvider();
        String endpoint = runtimeConfig.getChatEndpoint();
        String apiKey = runtimeConfig.getChatApiKey();
        String model = runtimeConfig.getChatModel();

        // Effective values with fallback to application.properties defaults
        String effEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : defaultAzureEndpoint;
        String effKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : defaultAzureApiKey;
        String effModel = (model != null && !model.isBlank()) ? model : defaultAzureDeployment;

        // Validate we have enough to build a client
        if (!hasValidConfig(provider, effEndpoint, effKey, effModel)) {
            return autoConfiguredChatModel; // may be null — caller handles
        }

        // If no custom UI config and auto-configured bean exists, use it (fast path)
        if ("AZURE".equals(provider)
                && (endpoint == null || endpoint.isBlank())
                && (apiKey == null || apiKey.isBlank())
                && (model == null || model.isBlank())
                && autoConfiguredChatModel != null) {
            return autoConfiguredChatModel;
        }

        String signature = signatureFor(provider, effEndpoint, effKey, effModel);
        if (cachedModel != null && Objects.equals(signature, cachedSignature)) {
            return cachedModel;
        }
        synchronized (this) {
            if (cachedModel != null && Objects.equals(signature, cachedSignature)) {
                return cachedModel;
            }
            try {
                cachedModel = buildModel(provider, effEndpoint, effKey, effModel);
                cachedSignature = signature;
                log.info("Dynamic ChatModel built — provider={}, model={}", provider, effModel);
            } catch (Exception e) {
                log.error("Failed to build dynamic ChatModel ({}): {}", provider, e.getMessage());
                cachedModel = null;
                return autoConfiguredChatModel;
            }
        }
        return cachedModel;
    }

    private boolean hasValidConfig(String provider, String endpoint, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) return false;
        if (model == null || model.isBlank()) return false;
        if ("AZURE".equals(provider) && (endpoint == null || endpoint.isBlank())) return false;
        return true;
    }

    private ChatModel buildModel(String provider, String endpoint, String apiKey, String model) {
        if ("OPENAI".equals(provider)) {
            return buildOpenAi(endpoint, apiKey, model);
        }
        return buildAzure(endpoint, apiKey, model);
    }

    private ChatModel buildAzure(String endpoint, String apiKey, String deployment) {
        String effEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : defaultAzureEndpoint;
        String effKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : defaultAzureApiKey;
        String effDeployment = (deployment != null && !deployment.isBlank()) ? deployment : defaultAzureDeployment;

        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .endpoint(effEndpoint)
                .credential(new AzureKeyCredential(effKey));
        OpenAIClient client = clientBuilder.buildClient();

        AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder()
                .deploymentName(effDeployment)
                .build();

        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(clientBuilder)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildOpenAi(String endpoint, String apiKey, String model) {
        String baseUrl = (endpoint != null && !endpoint.isBlank()) ? endpoint : "https://api.openai.com";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private String signatureFor(String provider, String endpoint, String apiKey, String model) {
        return provider + "|" + endpoint + "|" + (apiKey == null ? "" : apiKey.hashCode()) + "|" + model;
    }
}
