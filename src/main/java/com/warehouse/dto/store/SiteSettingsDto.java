package com.warehouse.dto.store;

import lombok.Data;
import java.util.Map;

@Data
public class SiteSettingsDto {
    private Map<String, String> settings;
}
