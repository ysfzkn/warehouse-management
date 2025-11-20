package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.import")
@Getter
@Setter
public class ImportProperties {

    /**
     * Directory where uploaded Excel files will be temporarily stored.
     * Defaults to a writable relative path for local development, but should
     * be overridden in production (e.g. /tmp/warehouse-imports on Railway).
     */
    private String storageDir = "uploads/stock-imports";
}


