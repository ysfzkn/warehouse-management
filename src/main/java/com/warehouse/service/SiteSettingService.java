package com.warehouse.service;

import com.warehouse.entity.SiteSetting;
import java.util.List;
import java.util.Map;

public interface SiteSettingService {
    Map<String, String> getAllSettings();
    String getSetting(String key);
    void updateSettings(Map<String, String> settings, String updatedBy);
    List<SiteSetting> getAllSettingEntities();
}
