package com.warehouse.assistant.core.config;

import com.warehouse.service.SiteSettingService;
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

    public static final String KEY_WMS_ENABLED   = "assistant_wms_enabled";
    public static final String KEY_STORE_ENABLED = "assistant_store_enabled";

    private final SiteSettingService siteSettingService;

    public AssistantFlagsService(SiteSettingService siteSettingService) {
        this.siteSettingService = siteSettingService;
    }

    public boolean isWmsEnabled() {
        return readBool(KEY_WMS_ENABLED, true);
    }

    public boolean isStoreEnabled() {
        return readBool(KEY_STORE_ENABLED, true);
    }

    public Map<String, Boolean> getAllFlags() {
        Map<String, Boolean> out = new HashMap<>();
        out.put("wmsEnabled", isWmsEnabled());
        out.put("storeEnabled", isStoreEnabled());
        return out;
    }

    public void updateFlags(Boolean wmsEnabled, Boolean storeEnabled, String updatedBy) {
        Map<String, String> patch = new HashMap<>();
        if (wmsEnabled != null)   patch.put(KEY_WMS_ENABLED,   wmsEnabled   ? "true" : "false");
        if (storeEnabled != null) patch.put(KEY_STORE_ENABLED, storeEnabled ? "true" : "false");
        if (!patch.isEmpty()) {
            siteSettingService.updateSettings(patch, updatedBy);
        }
    }

    private boolean readBool(String key, boolean defaultValue) {
        String raw = siteSettingService.getSetting(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        // Accept "true"/"false", "1"/"0", "on"/"off", case-insensitive.
        String v = raw.trim().toLowerCase();
        return !(v.equals("false") || v.equals("0") || v.equals("off") || v.equals("no"));
    }
}
