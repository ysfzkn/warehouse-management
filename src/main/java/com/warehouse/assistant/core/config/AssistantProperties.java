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

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }
    public Rag getRag() { return rag; }
    public void setRag(Rag rag) { this.rag = rag; }
    public RateLimit getRatelimit() { return ratelimit; }
    public void setRatelimit(RateLimit ratelimit) { this.ratelimit = ratelimit; }

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
        private int vectorTopK = 5;
        private double vectorDistanceThreshold = 0.35;
        private int chunkSizeTokens = 500;
        private int chunkOverlapTokens = 50;

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
}
