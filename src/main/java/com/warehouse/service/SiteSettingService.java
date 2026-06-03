package com.warehouse.service;

import com.warehouse.entity.SiteSetting;
import java.util.List;
import java.util.Map;

public interface SiteSettingService {
    Map<String, String> getAllSettings();
    String getSetting(String key);
    void updateSettings(Map<String, String> settings, String updatedBy);
    List<SiteSetting> getAllSettingEntities();

    /**
     * Reads a boolean setting. "true"/"1"/"on" → true, "false"/"0"/"off" → false.
     * Returns {@code defaultValue} if the setting is missing or cannot be parsed.
     */
    boolean getBoolSetting(String key, boolean defaultValue);
}
