package com.warehouse.assistant.core.config;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature flags for the assistant platform, backed by the existing
 * {@code site_settings} table. Each profile (WMS admin, Store) has an
 * independent on/off switch the admin can toggle from the dashboard without
 * a redeploy.
 * <p>
 * Default behaviour when the setting is absent is <b>enabled</b> — this
 * preserves backward compatibility for existing installs that upgrade to
 * v2 without touching their configuration.
 */
@Service
public class AssistantFlagsService {

    private static final Logger log = LoggerFactory.getLogger(AssistantFlagsService.class);

    public static final String KEY_WMS_ENABLED   = "assistant_wms_enabled";
    public static final String KEY_STORE_ENABLED = "assistant_store_enabled";

    private final SiteSettingService siteSettingService;

    public AssistantFlagsService(SiteSettingService siteSettingService) {
        this.siteSettingService = siteSettingService;
    }

    public boolean isWmsEnabled() {
        boolean v = readBool(KEY_WMS_ENABLED, true);
        log.trace("[AssistantFlags] read wmsEnabled={}", v);
        return v;
    }

    public boolean isStoreEnabled() {
        boolean v = readBool(KEY_STORE_ENABLED, true);
        log.trace("[AssistantFlags] read storeEnabled={}", v);
        return v;
    }

    public Map<String, Boolean> getAllFlags() {
        Map<String, Boolean> out = new HashMap<>();
        out.put("wmsEnabled", isWmsEnabled());
        out.put("storeEnabled", isStoreEnabled());
        log.debug("[AssistantFlags] public read → {}", out);
        return out;
    }

    public void updateFlags(Boolean wmsEnabled, Boolean storeEnabled, String updatedBy) {
        boolean beforeWms = isWmsEnabled();
        boolean beforeStore = isStoreEnabled();

        Map<String, String> patch = new HashMap<>();
        if (wmsEnabled != null)   patch.put(KEY_WMS_ENABLED,   wmsEnabled   ? "true" : "false");
        if (storeEnabled != null) patch.put(KEY_STORE_ENABLED, storeEnabled ? "true" : "false");

        if (patch.isEmpty()) {
            log.warn("[AssistantFlags] updateFlags called with nothing to change (user={})", updatedBy);
            return;
        }

        log.info("[AssistantFlags] TOGGLE by user='{}' — wms: {} → {}, store: {} → {}",
                updatedBy,
                beforeWms, (wmsEnabled != null ? wmsEnabled : "unchanged"),
                beforeStore, (storeEnabled != null ? storeEnabled : "unchanged"));

        siteSettingService.updateSettings(patch, updatedBy);

        boolean afterWms = isWmsEnabled();
        boolean afterStore = isStoreEnabled();
        log.info("[AssistantFlags] persisted state → wmsEnabled={}, storeEnabled={}", afterWms, afterStore);
    }

    private boolean readBool(String key, boolean defaultValue) {
        String raw = siteSettingService.getSetting(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        String v = raw.trim().toLowerCase();
        return !(v.equals("false") || v.equals("0") || v.equals("off") || v.equals("no"));
    }
}
