package com.warehouse.cezeri.config;

import com.warehouse.cezeri.tools.CezeriCatalogTools;
import com.warehouse.cezeri.tools.CezeriAdminTools;
import com.warehouse.cezeri.tools.CezeriStockTools;
import com.warehouse.cezeri.tools.CezeriUserTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Central place for Cezeri AI wiring (ChatClient + tool registration).
 * Supports both Azure OpenAI and Ollama providers.
 * Provider selection via app.ai.provider property (azure|ollama).
 * 
 * Spring AI auto-configuration creates ChatModel beans for both providers.
 * We use @Primary to ensure the selected provider's ChatModel is used by ChatClient.Builder.
 */
@Configuration
@Profile("!test")
public class CezeriAiConfig {

    private static final Logger log = LoggerFactory.getLogger(CezeriAiConfig.class);

    @Value("${" + AiProvider.PROPERTY_KEY + ":" + AiProvider.DEFAULT_VALUE + "}")
    private String aiProvider;

    /**
     * Makes Azure OpenAI ChatModel primary when AI_PROVIDER=azure.
     * This ensures ChatClient.Builder uses Azure OpenAI instead of Ollama.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = AiProvider.PROPERTY_KEY, havingValue = AiProvider.AZURE_VALUE)
    public ChatModel azurePrimaryChatModel(AzureOpenAiChatModel azureChatModel) {
        log.info("Setting Azure OpenAI as primary ChatModel provider");
        return azureChatModel;
    }

    /**
     * Makes Ollama ChatModel primary when AI_PROVIDER=ollama (default).
     * This ensures ChatClient.Builder uses Ollama instead of Azure OpenAI.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = AiProvider.PROPERTY_KEY, havingValue = AiProvider.OLLAMA_VALUE, matchIfMissing = true)
    public ChatModel ollamaPrimaryChatModel(OllamaChatModel ollamaChatModel) {
        log.info("Setting Ollama as primary ChatModel provider");
        return ollamaChatModel;
    }

    @Bean
    public ChatClient cezeriChatClientUser(ChatClient.Builder builder,
                                           CezeriCatalogTools catalogTools,
                                           CezeriStockTools stockTools,
                                           CezeriUserTools userTools) {
        log.info("Initializing Cezeri ChatClient for users with provider: {}", aiProvider);
        // Non-admin tool set
        // ChatClient.Builder automatically uses @Primary ChatModel
        return builder.defaultTools(catalogTools, stockTools, userTools).build();
    }

    @Bean
    public ChatClient cezeriChatClientAdmin(ChatClient.Builder builder,
                                            CezeriCatalogTools catalogTools,
                                            CezeriStockTools stockTools,
                                            CezeriUserTools userTools,
                                            CezeriAdminTools adminTools) {
        log.info("Initializing Cezeri ChatClient for admins with provider: {}", aiProvider);
        // Admin tool set (includes admin-only tools)
        // ChatClient.Builder automatically uses @Primary ChatModel
        return builder.defaultTools(catalogTools, stockTools, userTools, adminTools).build();
    }
}


