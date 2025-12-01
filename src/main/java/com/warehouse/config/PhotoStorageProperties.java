package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.photo")
@Getter
@Setter
public class PhotoStorageProperties {

    /**
     * Base directory where shipment photos will be stored.
     * Defaults to a relative path suitable for local development.
     */
    private String baseDir = "uploads/shipments";

    /**
     * Maximum width for the stored (optimized) image in pixels.
     */
    private int maxWidth = 1920;

    /**
     * Maximum height for the stored (optimized) image in pixels.
     */
    private int maxHeight = 1920;

    /**
     * JPEG/WebP compression quality between 0 and 1.
     */
    private float quality = 0.75f;

    /**
     * Maximum allowed file size in megabytes (validated at API level).
     */
    private int maxFileSizeMb = 5;
}


