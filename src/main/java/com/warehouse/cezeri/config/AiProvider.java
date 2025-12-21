package com.warehouse.cezeri.config;

/**
 * AI Provider types supported by Cezeri AI assistant.
 * Used to select between Azure OpenAI and Ollama providers.
 */
public enum AiProvider {
    
    /**
     * Azure OpenAI provider (cloud-based, paid service).
     * Requires: AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY, AZURE_OPENAI_GPT51_DEPLOYMENT
     */
    AZURE,
    
    /**
     * Ollama provider (self-hosted, free).
     * Requires: OLLAMA_BASE_URL, OLLAMA_MODEL
     */
    OLLAMA;
    
    /**
     * Property key for AI provider selection in application properties.
     */
    public static final String PROPERTY_KEY = "app.ai.provider";
    
    /**
     * String values for each provider (used in configuration files and annotations).
     */
    public static final String AZURE_VALUE = "azure";
    public static final String OLLAMA_VALUE = "ollama";
    
    /**
     * Default provider value when not specified.
     */
    public static final String DEFAULT_VALUE = OLLAMA_VALUE;
    
    /**
     * Default provider when not specified.
     */
    public static final AiProvider DEFAULT = OLLAMA;
    
    /**
     * Returns the string value of the provider (used in configuration files).
     * @return provider value as string
     */
    public String getValue() {
        return this == AZURE ? AZURE_VALUE : OLLAMA_VALUE;
    }
    
    /**
     * Converts a string value to AiProvider enum.
     * @param value the string value (case-insensitive)
     * @return corresponding AiProvider, or DEFAULT if not found
     */
    public static AiProvider fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        if (AZURE_VALUE.equalsIgnoreCase(value)) {
            return AZURE;
        }
        if (OLLAMA_VALUE.equalsIgnoreCase(value)) {
            return OLLAMA;
        }
        return DEFAULT;
    }
}

