package com.warehouse.service.impl;

import com.warehouse.entity.SiteSetting;
import com.warehouse.repository.SiteSettingRepository;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SiteSettingServiceImpl implements SiteSettingService {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingServiceImpl.class);

    /** site_settings.setting_key VARCHAR(100) — DB limit */
    private static final int MAX_KEY_LENGTH = 100;
    /** site_settings.updated_by VARCHAR(100) — DB limit */
    private static final int MAX_UPDATED_BY_LENGTH = 100;

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

    /**
     * Key-name substrings that mark a setting as a credential/secret. Any key
     * containing one of these (case-insensitive) is NEVER sent to the public
     * storefront — prevents SMS/cargo/e-invoice credentials from leaking via
     * /api/store/settings.
     */
    private static final List<String> SENSITIVE_KEY_PATTERNS = List.of(
        "password", "secret", "token", "api_key", "apikey", "app_key", "appkey",
        "private", "webhook", "credential", "username"
    );

    /**
     * Segment-level denylist for {@code GET /api/store/settings}, which is unauthenticated.
     *
     * <p>The substring check alone was too narrow: {@code logo_efatura_endpoint},
     * {@code kargonomi_api_base_url}, {@code kargonomi_warehouse_id},
     * {@code invoice_admin_digest_email} and {@code logo_company_bank_iban} matched none
     * of its patterns and were served to anonymous visitors — internal integration
     * endpoints, an administrator's address, and the company IBAN.</p>
     *
     * <p>Matching on underscore-delimited segments rather than raw substrings is what
     * makes a wider list usable: {@code seo_local_keywords_extra} contains "key" but its
     * segments are seo/local/keywords/extra, so it stays public, while
     * {@code kargonomi_app_key} is blocked on the "key" segment.</p>
     */
    private static final Set<String> SENSITIVE_KEY_SEGMENTS = Set.of(
            "password", "passwd", "pass", "secret", "token", "key", "apikey", "appkey",
            "credential", "credentials", "username", "user", "iban", "merchant",
            "private", "internal", "admin", "hash", "salt", "endpoint", "webhook",
            "signature", "certificate", "cert"
    );

    private static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) return true;
        String k = key.toLowerCase();
        if (SENSITIVE_KEY_PATTERNS.stream().anyMatch(k::contains)) return true;
        for (String segment : k.split("[_.\\-]")) {
            if (SENSITIVE_KEY_SEGMENTS.contains(segment)) return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        return repository.findAll().stream()
            .filter(s -> !isSensitiveKey(s.getSettingKey()))
            .filter(s -> s.getSettingValue() != null)
            .collect(Collectors.toMap(SiteSetting::getSettingKey, SiteSetting::getSettingValue));
    }

    @Override
    @Transactional(readOnly = true)
    public String getSetting(String key) {
        return repository.findBySettingKey(key).map(SiteSetting::getSettingValue).orElse("");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolSetting(String key, boolean defaultValue) {
        String v = repository.findBySettingKey(key).map(SiteSetting::getSettingValue).orElse(null);
        if (v == null || v.isBlank()) return defaultValue;
        String lv = v.trim().toLowerCase();
        if ("true".equals(lv) || "1".equals(lv) || "on".equals(lv) || "yes".equals(lv)) return true;
        if ("false".equals(lv) || "0".equals(lv) || "off".equals(lv) || "no".equals(lv)) return false;
        return defaultValue;
    }

    @Override
    public void updateSettings(Map<String, String> settings, String updatedBy) {
        if (settings == null || settings.isEmpty()) {
            log.warn("updateSettings called with empty/null map — ignoring");
            return;
        }
        // Truncate the updated_by field to the DB limit (so a too-long CurrentUser.usernameOrSystem() does not error)
        final String safeUpdatedBy = updatedBy == null ? "system"
                : (updatedBy.length() > MAX_UPDATED_BY_LENGTH
                    ? updatedBy.substring(0, MAX_UPDATED_BY_LENGTH) : updatedBy);

        int updated = 0, skipped = 0;
        for (var entry : settings.entrySet()) {
            String key = entry.getKey();
            // Skip invalid keys: null, blank, too long
            if (key == null || key.isBlank()) {
                log.warn("Skipping setting with null/blank key");
                skipped++;
                continue;
            }
            if (key.length() > MAX_KEY_LENGTH) {
                log.warn("Skipping setting key too long ({} > {}): {}", key.length(), MAX_KEY_LENGTH, key);
                skipped++;
                continue;
            }
            try {
                SiteSetting setting = repository.findBySettingKey(key).orElse(null);
                if (setting == null) {
                    setting = new SiteSetting();
                    setting.setSettingKey(key);
                    setting.setSettingType("STRING");
                }
                // PostgreSQL TEXT NOT NULL constraint — null → ""
                setting.setSettingValue(entry.getValue() != null ? entry.getValue() : "");
                setting.setUpdatedBy(safeUpdatedBy);
                repository.save(setting);
                updated++;
            } catch (Exception ex) {
                // If a single setting throws, do not lose the others — skip this key
                log.error("Failed to save setting key='{}': {}", key, ex.getMessage(), ex);
                throw new IllegalStateException(
                        "Ayar kaydedilemedi (key=" + key + "): " + ex.getMessage(), ex);
            }
        }
        log.info("Site settings updated: {} keys, {} skipped, updatedBy={}", updated, skipped, safeUpdatedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteSetting> getAllSettingEntities() {
        return repository.findAll();
    }
}
