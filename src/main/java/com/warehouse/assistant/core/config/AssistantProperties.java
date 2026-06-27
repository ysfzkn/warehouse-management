package com.warehouse.assistant.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Typed access to all {@code assistant.*} configuration. Keeping this in one
 * place means the rest of the codebase doesn't pepper {@code @Value} annotations
 * around and everything flows through a single, testable object.
 */
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private Cost cost = new Cost();
    private Rag rag = new Rag();
    private RateLimit ratelimit = new RateLimit();
    private Safety safety = new Safety();
    private EmbeddingConnection embedding = new EmbeddingConnection();
    private ChatConnection chat = new ChatConnection();
    private Image image = new Image();

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }
    public Rag getRag() { return rag; }
    public void setRag(Rag rag) { this.rag = rag; }
    public RateLimit getRatelimit() { return ratelimit; }
    public void setRatelimit(RateLimit ratelimit) { this.ratelimit = ratelimit; }
    public Safety getSafety() { return safety; }
    public void setSafety(Safety safety) { this.safety = safety; }
    public EmbeddingConnection getEmbedding() { return embedding; }
    public void setEmbedding(EmbeddingConnection embedding) { this.embedding = embedding; }
    public ChatConnection getChat() { return chat; }
    public void setChat(ChatConnection chat) { this.chat = chat; }
    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    /** AI provider types. */
    public enum Provider { AZURE, OPENAI }

    // ---------- cost ----------
    public static class Cost {
        private Chat chat = new Chat();
        private Embedding embedding = new Embedding();
        public Chat getChat() { return chat; }
        public void setChat(Chat chat) { this.chat = chat; }
        public Embedding getEmbedding() { return embedding; }
        public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

        public static class Chat {
            private BigDecimal promptPer1kUsd = new BigDecimal("0.0025");
            private BigDecimal completionPer1kUsd = new BigDecimal("0.010");
            public BigDecimal getPromptPer1kUsd() { return promptPer1kUsd; }
            public void setPromptPer1kUsd(BigDecimal v) { this.promptPer1kUsd = v; }
            public BigDecimal getCompletionPer1kUsd() { return completionPer1kUsd; }
            public void setCompletionPer1kUsd(BigDecimal v) { this.completionPer1kUsd = v; }
        }

        public static class Embedding {
            private BigDecimal per1kUsd = new BigDecimal("0.00002");
            public BigDecimal getPer1kUsd() { return per1kUsd; }
            public void setPer1kUsd(BigDecimal v) { this.per1kUsd = v; }
        }
    }

    // ---------- RAG ----------
    public static class Rag {
        private int embeddingDimensions = 1536;
        /** Top-K neighbours to consider. Was 5 → bumped to 8 so a borderline-relevant chunk
         *  still has a seat at the table. */
        private int vectorTopK = 8;
        /** Cosine-distance cut-off. Was 0.35 (too strict for Turkish + small embedding model).
         *  0.55 lets moderately related chunks through; the LLM filters the rest. */
        private double vectorDistanceThreshold = 0.55;
        /** Chunk size in words. Was 500 (too coarse — multi-topic chunks dilute embedding).
         *  200 words ≈ 1 paragraph, keeps each chunk semantically focused. */
        private int chunkSizeTokens = 200;
        /** Overlap between consecutive chunks, in words. 50 = 25% overlap, preserves context
         *  across chunk boundaries. */
        private int chunkOverlapTokens = 50;
        /** Neighbor window: when a chunk matches, also include this many chunks before
         *  and after from the same document to preserve context (small chunks read well
         *  but may lose the parent section). 0 = disabled, 2 = hit±2 chunks (recommended). */
        private int neighborWindow = 2;
        /** Enable hybrid retrieval: combines pgvector semantic search with
         *  PostgreSQL full-text BM25-ish scoring via Reciprocal Rank Fusion.
         *  Catches exact-term queries (SKU, "MADDE 6") that vector search alone
         *  may miss. */
        private boolean hybridEnabled = true;
        /** RRF constant k — standard value is 60. Larger = less weight to top ranks. */
        private int hybridRrfK = 60;

        public int getEmbeddingDimensions() { return embeddingDimensions; }
        public void setEmbeddingDimensions(int v) { this.embeddingDimensions = v; }
        public int getVectorTopK() { return vectorTopK; }
        public void setVectorTopK(int v) { this.vectorTopK = v; }
        public double getVectorDistanceThreshold() { return vectorDistanceThreshold; }
        public void setVectorDistanceThreshold(double v) { this.vectorDistanceThreshold = v; }
        public int getChunkSizeTokens() { return chunkSizeTokens; }
        public void setChunkSizeTokens(int v) { this.chunkSizeTokens = v; }
        public int getChunkOverlapTokens() { return chunkOverlapTokens; }
        public void setChunkOverlapTokens(int v) { this.chunkOverlapTokens = v; }
        public int getNeighborWindow() { return neighborWindow; }
        public void setNeighborWindow(int v) { this.neighborWindow = v; }
        public boolean isHybridEnabled() { return hybridEnabled; }
        public void setHybridEnabled(boolean v) { this.hybridEnabled = v; }
        public int getHybridRrfK() { return hybridRrfK; }
        public void setHybridRrfK(int v) { this.hybridRrfK = v; }
    }

    // ---------- rate limit ----------
    public static class RateLimit {
        private int guestSessionMax = 2;
        private int guestIpDaily = 10;
        private int customerHourly = 30;
        private int customerDaily = 150;
        private int wmsHourly = 60;

        public int getGuestSessionMax() { return guestSessionMax; }
        public void setGuestSessionMax(int v) { this.guestSessionMax = v; }
        public int getGuestIpDaily() { return guestIpDaily; }
        public void setGuestIpDaily(int v) { this.guestIpDaily = v; }
        public int getCustomerHourly() { return customerHourly; }
        public void setCustomerHourly(int v) { this.customerHourly = v; }
        public int getCustomerDaily() { return customerDaily; }
        public void setCustomerDaily(int v) { this.customerDaily = v; }
        public int getWmsHourly() { return wmsHourly; }
        public void setWmsHourly(int v) { this.wmsHourly = v; }
    }

    // ---------- embedding connection (Azure or vanilla OpenAI) ----------
    public static class EmbeddingConnection {
        private Provider provider = Provider.AZURE;
        private String endpoint = "";          // Azure endpoint; empty = use main chat endpoint
        private String apiKey = "";            // empty = use main chat api-key
        private String deploymentName = "text-embedding-3-small"; // Azure deployment OR OpenAI model id

        public Provider getProvider() { return provider; }
        public void setProvider(Provider v) { this.provider = v; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { this.endpoint = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getDeploymentName() { return deploymentName; }
        public void setDeploymentName(String v) { this.deploymentName = v; }

        public boolean hasSeparateEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }
    }

    // ---------- chat connection (Azure or vanilla OpenAI) ----------
    public static class ChatConnection {
        private Provider provider = Provider.AZURE;
        private String endpoint = "";              // Azure endpoint; OpenAI uses default https://api.openai.com
        private String apiKey = "";
        private String model = "gpt-4o-mini";      // Azure deployment name OR OpenAI model id

        public Provider getProvider() { return provider; }
        public void setProvider(Provider v) { this.provider = v; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { this.endpoint = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
    }

    // ---------- image generation (OpenAI Images API — AI set cover) ----------
    public static class Image {
        private String apiKey = "";                  // OpenAI API key (vanilla; not Azure)
        private String endpoint = "";                // empty = https://api.openai.com
        private String model = "gpt-image-1";        // best logo/text fidelity (input_fidelity=high)
        private String quality = "medium";           // low | medium | high
        private String size = "1024x1024";           // 1024x1024 | 1536x1024 | 1024x1536 | auto
        private String prompt = "";                  // empty = built-in default combine prompt

        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { this.endpoint = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getQuality() { return quality; }
        public void setQuality(String v) { this.quality = v; }
        public String getSize() { return size; }
        public void setSize(String v) { this.size = v; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String v) { this.prompt = v; }
    }

    // ---------- safety ----------
    public static class Safety {
        private Input input = new Input();
        private Output output = new Output();
        private boolean logSanitization = true;

        public Input getInput() { return input; }
        public void setInput(Input input) { this.input = input; }
        public Output getOutput() { return output; }
        public void setOutput(Output output) { this.output = output; }
        public boolean isLogSanitization() { return logSanitization; }
        public void setLogSanitization(boolean v) { this.logSanitization = v; }

        public static class Input {
            private int maxLength = 4000;
            private boolean piiDetection = true;
            private String piiAction = "REDACT"; // REDACT | WARN | BLOCK
            private boolean jailbreakDetection = true;

            public int getMaxLength() { return maxLength; }
            public void setMaxLength(int v) { this.maxLength = v; }
            public boolean isPiiDetection() { return piiDetection; }
            public void setPiiDetection(boolean v) { this.piiDetection = v; }
            public String getPiiAction() { return piiAction; }
            public void setPiiAction(String v) { this.piiAction = v; }
            public boolean isJailbreakDetection() { return jailbreakDetection; }
            public void setJailbreakDetection(boolean v) { this.jailbreakDetection = v; }
        }

        public static class Output {
            private boolean piiRedaction = true;
            private boolean hallucinationCheck = true;

            public boolean isPiiRedaction() { return piiRedaction; }
            public void setPiiRedaction(boolean v) { this.piiRedaction = v; }
            public boolean isHallucinationCheck() { return hallucinationCheck; }
            public void setHallucinationCheck(boolean v) { this.hallucinationCheck = v; }
        }
    }
}
