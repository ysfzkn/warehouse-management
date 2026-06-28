package com.warehouse.assistant.core.config;

import com.warehouse.service.SiteSettingService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-editable assistant configuration. Reads from the {@code site_settings}
 * table first (admin-changed values via the UI), then falls back to the
 * {@link AssistantProperties} boot defaults from {@code application.properties}.
 * <p>
 * This lets the admin tune safety thresholds, rate limits, and RAG parameters
 * without a redeploy — changes take effect on the next chat request.
 * <p>
 * All keys in site_settings are prefixed with {@code assistant.} to avoid
 * collision with other settings.
 */
@Service
public class AssistantRuntimeConfig {

    private final SiteSettingService siteSettings;
    private final AssistantProperties defaults;

    public AssistantRuntimeConfig(SiteSettingService siteSettings, AssistantProperties defaults) {
        this.siteSettings = siteSettings;
        this.defaults = defaults;
    }

    // ── Safety: Input ──
    public int getInputMaxLength() {
        return getInt("assistant.safety.input.max-length", defaults.getSafety().getInput().getMaxLength());
    }
    public boolean isInputPiiDetection() {
        return getBool("assistant.safety.input.pii-detection", defaults.getSafety().getInput().isPiiDetection());
    }
    public String getInputPiiAction() {
        return getString("assistant.safety.input.pii-action", defaults.getSafety().getInput().getPiiAction());
    }
    public boolean isInputJailbreakDetection() {
        return getBool("assistant.safety.input.jailbreak-detection", defaults.getSafety().getInput().isJailbreakDetection());
    }

    // ── Safety: Output ──
    public boolean isOutputPiiRedaction() {
        return getBool("assistant.safety.output.pii-redaction", defaults.getSafety().getOutput().isPiiRedaction());
    }
    public boolean isOutputHallucinationCheck() {
        return getBool("assistant.safety.output.hallucination-check", defaults.getSafety().getOutput().isHallucinationCheck());
    }
    public boolean isLogSanitization() {
        return getBool("assistant.safety.log-sanitization", defaults.getSafety().isLogSanitization());
    }

    // ── Rate Limits ──
    public int getGuestSessionMax() {
        return getInt("assistant.ratelimit.guest-session-max", defaults.getRatelimit().getGuestSessionMax());
    }
    public int getGuestIpDaily() {
        return getInt("assistant.ratelimit.guest-ip-daily", defaults.getRatelimit().getGuestIpDaily());
    }
    public int getCustomerHourly() {
        return getInt("assistant.ratelimit.customer-hourly", defaults.getRatelimit().getCustomerHourly());
    }
    public int getCustomerDaily() {
        return getInt("assistant.ratelimit.customer-daily", defaults.getRatelimit().getCustomerDaily());
    }
    public int getWmsHourly() {
        return getInt("assistant.ratelimit.wms-hourly", defaults.getRatelimit().getWmsHourly());
    }

    // ── RAG ──
    public int getVectorTopK() {
        return getInt("assistant.rag.vector-top-k", defaults.getRag().getVectorTopK());
    }
    public double getVectorDistanceThreshold() {
        return getDouble("assistant.rag.vector-distance-threshold", defaults.getRag().getVectorDistanceThreshold());
    }
    public int getChunkSizeTokens() {
        return getInt("assistant.rag.chunk-size-tokens", defaults.getRag().getChunkSizeTokens());
    }
    public int getChunkOverlapTokens() {
        return getInt("assistant.rag.chunk-overlap-tokens", defaults.getRag().getChunkOverlapTokens());
    }
    public int getNeighborWindow() {
        return getInt("assistant.rag.neighbor-window", defaults.getRag().getNeighborWindow());
    }
    public boolean isHybridEnabled() {
        return getBool("assistant.rag.hybrid-enabled", defaults.getRag().isHybridEnabled());
    }
    public int getHybridRrfK() {
        return getInt("assistant.rag.hybrid-rrf-k", defaults.getRag().getHybridRrfK());
    }

    // ── Embedding connection (Azure or OpenAI) ──
    public String getEmbeddingProvider() {
        String v = getString("assistant.embedding.provider", defaults.getEmbedding().getProvider().name());
        return normalizeProvider(v);
    }
    public String getEmbeddingEndpoint() {
        return getString("assistant.embedding.endpoint", defaults.getEmbedding().getEndpoint());
    }
    public String getEmbeddingApiKey() {
        return getString("assistant.embedding.api-key", defaults.getEmbedding().getApiKey());
    }
    public String getEmbeddingDeploymentName() {
        return getString("assistant.embedding.deployment-name", defaults.getEmbedding().getDeploymentName());
    }

    // ── Chat connection (Azure or OpenAI) ──
    public String getChatProvider() {
        String v = getString("assistant.chat.provider", defaults.getChat().getProvider().name());
        return normalizeProvider(v);
    }
    public String getChatEndpoint() {
        return getString("assistant.chat.endpoint", defaults.getChat().getEndpoint());
    }
    public String getChatApiKey() {
        return getString("assistant.chat.api-key", defaults.getChat().getApiKey());
    }
    public String getChatModel() {
        return getString("assistant.chat.model", defaults.getChat().getModel());
    }

    // ── Image generation connection (OpenAI Images API — AI set cover) ──
    public String getImageApiKey() {
        return getString("assistant.image.api-key", defaults.getImage().getApiKey());
    }
    public String getImageEndpoint() {
        return getString("assistant.image.endpoint", defaults.getImage().getEndpoint());
    }
    public String getImageModel() {
        return getString("assistant.image.model", defaults.getImage().getModel());
    }
    public String getImageQuality() {
        return getString("assistant.image.quality", defaults.getImage().getQuality());
    }
    public String getImageSize() {
        return getString("assistant.image.size", defaults.getImage().getSize());
    }
    public String getImagePrompt() {
        return getString("assistant.image.prompt", defaults.getImage().getPrompt());
    }
    /**
     * Set-cover engine: {@code DIGITAL} (free local compositor, default) or
     * {@code AI} (OpenAI Images). Settable via the {@code ASSISTANT_IMAGE_MODE}
     * env var / {@code assistant.image.mode} property, or the admin settings.
     * Anything other than {@code AI} (case-insensitive) means DIGITAL.
     */
    public String getImageCoverMode() {
        String raw = getString("assistant.image.mode", defaults.getImage().getMode());
        return "AI".equalsIgnoreCase(raw == null ? null : raw.trim()) ? "AI" : "DIGITAL";
    }
    public boolean isAiCoverMode() {
        return "AI".equals(getImageCoverMode());
    }

    private String normalizeProvider(String v) {
        if (v == null) return "AZURE";
        String up = v.trim().toUpperCase();
        return ("OPENAI".equals(up)) ? "OPENAI" : "AZURE";
    }

    // ── Max Tokens ──
    public int getMaxResponseTokens() {
        return getInt("assistant.max-response-tokens", 1500);
    }

    // ── Bulk read for admin UI ──
    public Map<String, Object> getAllForAdmin() {
        Map<String, Object> m = new LinkedHashMap<>();
        // Safety
        m.put("safety.input.maxLength", getInputMaxLength());
        m.put("safety.input.piiDetection", isInputPiiDetection());
        m.put("safety.input.piiAction", getInputPiiAction());
        m.put("safety.input.jailbreakDetection", isInputJailbreakDetection());
        m.put("safety.output.piiRedaction", isOutputPiiRedaction());
        m.put("safety.output.hallucinationCheck", isOutputHallucinationCheck());
        m.put("safety.logSanitization", isLogSanitization());
        // Rate limits
        m.put("ratelimit.guestSessionMax", getGuestSessionMax());
        m.put("ratelimit.guestIpDaily", getGuestIpDaily());
        m.put("ratelimit.customerHourly", getCustomerHourly());
        m.put("ratelimit.customerDaily", getCustomerDaily());
        m.put("ratelimit.wmsHourly", getWmsHourly());
        // RAG
        m.put("rag.vectorTopK", getVectorTopK());
        m.put("rag.vectorDistanceThreshold", getVectorDistanceThreshold());
        m.put("rag.chunkSizeTokens", getChunkSizeTokens());
        m.put("rag.chunkOverlapTokens", getChunkOverlapTokens());
        m.put("rag.neighborWindow", getNeighborWindow());
        m.put("rag.hybridEnabled", isHybridEnabled());
        m.put("rag.hybridRrfK", getHybridRrfK());
        // Max tokens
        m.put("maxResponseTokens", getMaxResponseTokens());
        // Embedding connection
        m.put("embedding.provider", getEmbeddingProvider());
        m.put("embedding.endpoint", getEmbeddingEndpoint());
        m.put("embedding.apiKey", maskSecret(getEmbeddingApiKey()));
        m.put("embedding.deploymentName", getEmbeddingDeploymentName());
        // Chat connection
        m.put("chat.provider", getChatProvider());
        m.put("chat.endpoint", getChatEndpoint());
        m.put("chat.apiKey", maskSecret(getChatApiKey()));
        m.put("chat.model", getChatModel());
        // Image generation connection
        m.put("image.mode", getImageCoverMode());
        m.put("image.apiKey", maskSecret(getImageApiKey()));
        m.put("image.endpoint", getImageEndpoint());
        m.put("image.model", getImageModel());
        m.put("image.quality", getImageQuality());
        m.put("image.size", getImageSize());
        m.put("image.prompt", getImagePrompt());
        return m;
    }

    /**
     * Admin bulk-update. Keys map to {@code assistant.*} site_settings entries.
     * Only provided keys are updated; nulls are skipped.
     */
    public void updateFromAdmin(Map<String, Object> values, String updatedBy) {
        Map<String, String> patch = new LinkedHashMap<>();
        putIfPresent(values, patch, "safety.input.maxLength", "assistant.safety.input.max-length");
        putIfPresent(values, patch, "safety.input.piiDetection", "assistant.safety.input.pii-detection");
        putIfPresent(values, patch, "safety.input.piiAction", "assistant.safety.input.pii-action");
        putIfPresent(values, patch, "safety.input.jailbreakDetection", "assistant.safety.input.jailbreak-detection");
        putIfPresent(values, patch, "safety.output.piiRedaction", "assistant.safety.output.pii-redaction");
        putIfPresent(values, patch, "safety.output.hallucinationCheck", "assistant.safety.output.hallucination-check");
        putIfPresent(values, patch, "safety.logSanitization", "assistant.safety.log-sanitization");
        putIfPresent(values, patch, "ratelimit.guestSessionMax", "assistant.ratelimit.guest-session-max");
        putIfPresent(values, patch, "ratelimit.guestIpDaily", "assistant.ratelimit.guest-ip-daily");
        putIfPresent(values, patch, "ratelimit.customerHourly", "assistant.ratelimit.customer-hourly");
        putIfPresent(values, patch, "ratelimit.customerDaily", "assistant.ratelimit.customer-daily");
        putIfPresent(values, patch, "ratelimit.wmsHourly", "assistant.ratelimit.wms-hourly");
        putIfPresent(values, patch, "rag.vectorTopK", "assistant.rag.vector-top-k");
        putIfPresent(values, patch, "rag.vectorDistanceThreshold", "assistant.rag.vector-distance-threshold");
        putIfPresent(values, patch, "rag.chunkSizeTokens", "assistant.rag.chunk-size-tokens");
        putIfPresent(values, patch, "rag.chunkOverlapTokens", "assistant.rag.chunk-overlap-tokens");
        putIfPresent(values, patch, "rag.neighborWindow", "assistant.rag.neighbor-window");
        putIfPresent(values, patch, "rag.hybridEnabled", "assistant.rag.hybrid-enabled");
        putIfPresent(values, patch, "rag.hybridRrfK", "assistant.rag.hybrid-rrf-k");
        putIfPresent(values, patch, "maxResponseTokens", "assistant.max-response-tokens");
        // Embedding connection
        putIfPresent(values, patch, "embedding.provider", "assistant.embedding.provider");
        putIfPresent(values, patch, "embedding.endpoint", "assistant.embedding.endpoint");
        Object embKey = values.get("embedding.apiKey");
        if (embKey != null && !String.valueOf(embKey).contains("****")) {
            patch.put("assistant.embedding.api-key", String.valueOf(embKey));
        }
        putIfPresent(values, patch, "embedding.deploymentName", "assistant.embedding.deployment-name");
        // Chat connection
        putIfPresent(values, patch, "chat.provider", "assistant.chat.provider");
        putIfPresent(values, patch, "chat.endpoint", "assistant.chat.endpoint");
        Object chatKey = values.get("chat.apiKey");
        if (chatKey != null && !String.valueOf(chatKey).contains("****")) {
            patch.put("assistant.chat.api-key", String.valueOf(chatKey));
        }
        putIfPresent(values, patch, "chat.model", "assistant.chat.model");
        // Image generation connection
        Object imageKey = values.get("image.apiKey");
        if (imageKey != null && !String.valueOf(imageKey).contains("****")) {
            patch.put("assistant.image.api-key", String.valueOf(imageKey));
        }
        putIfPresent(values, patch, "image.endpoint", "assistant.image.endpoint");
        putIfPresent(values, patch, "image.model", "assistant.image.model");
        putIfPresent(values, patch, "image.quality", "assistant.image.quality");
        putIfPresent(values, patch, "image.size", "assistant.image.size");
        putIfPresent(values, patch, "image.prompt", "assistant.image.prompt");
        putIfPresent(values, patch, "image.mode", "assistant.image.mode");
        if (!patch.isEmpty()) {
            siteSettings.updateSettings(patch, updatedBy);
        }
    }

    // ── Helpers ──
    private String getString(String key, String defaultVal) {
        String v = siteSettings.getSetting(key);
        return (v != null && !v.isBlank()) ? v.trim() : defaultVal;
    }
    private int getInt(String key, int defaultVal) {
        String v = siteSettings.getSetting(key);
        if (v == null || v.isBlank()) return defaultVal;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }
    private double getDouble(String key, double defaultVal) {
        String v = siteSettings.getSetting(key);
        if (v == null || v.isBlank()) return defaultVal;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }
    private boolean getBool(String key, boolean defaultVal) {
        String v = siteSettings.getSetting(key);
        if (v == null || v.isBlank()) return defaultVal;
        String lv = v.trim().toLowerCase();
        if ("true".equals(lv) || "1".equals(lv) || "on".equals(lv)) return true;
        if ("false".equals(lv) || "0".equals(lv) || "off".equals(lv)) return false;
        return defaultVal;
    }
    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) return "";
        if (secret.length() <= 8) return "****";
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

    private void putIfPresent(Map<String, Object> src, Map<String, String> dst, String srcKey, String dstKey) {
        Object val = src.get(srcKey);
        if (val != null) dst.put(dstKey, String.valueOf(val));
    }
}
