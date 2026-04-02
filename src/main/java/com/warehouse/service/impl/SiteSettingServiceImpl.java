package com.warehouse.service.impl;

import com.warehouse.entity.SiteSetting;
import com.warehouse.repository.SiteSettingRepository;
import com.warehouse.service.SiteSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class SiteSettingServiceImpl implements SiteSettingService {

    private final SiteSettingRepository repository;

    public SiteSettingServiceImpl(SiteSettingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAllSettings() {
        return repository.findAll().stream()
            .collect(Collectors.toMap(SiteSetting::getSettingKey, SiteSetting::getSettingValue));
    }

    @Override
    @Transactional(readOnly = true)
    public String getSetting(String key) {
        return repository.findBySettingKey(key).map(SiteSetting::getSettingValue).orElse("");
    }

    @Override
    public void updateSettings(Map<String, String> settings, String updatedBy) {
        for (var entry : settings.entrySet()) {
            SiteSetting setting = repository.findBySettingKey(entry.getKey()).orElse(null);
            if (setting == null) {
                setting = new SiteSetting();
                setting.setSettingKey(entry.getKey());
                setting.setSettingType("STRING");
            }
            setting.setSettingValue(entry.getValue() != null ? entry.getValue() : "");
            setting.setUpdatedBy(updatedBy);
            repository.save(setting);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteSetting> getAllSettingEntities() {
        return repository.findAll();
    }
}
