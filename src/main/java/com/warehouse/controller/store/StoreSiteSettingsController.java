package com.warehouse.controller.store;

import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.SiteSettingService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/store/settings")
public class StoreSiteSettingsController {

    private final SiteSettingService siteSettingService;
    private final PhotoStorageService photoStorageService;

    public StoreSiteSettingsController(SiteSettingService siteSettingService,
                                       PhotoStorageService photoStorageService) {
        this.siteSettingService = siteSettingService;
        this.photoStorageService = photoStorageService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getSettings() {
        return ResponseEntity.ok(siteSettingService.getAllSettings());
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> viewLogo() {
        return serveSiteAsset("site_logo");
    }

    @GetMapping("/favicon")
    public ResponseEntity<byte[]> viewFavicon() {
        return serveSiteAsset("site_favicon");
    }

    /**
     * Bank transfer/EFT FAST QR image — for the customer on the checkout page.
     * Fetches from the site_settings.bank_transfer_qr_image storage key.
     */
    @GetMapping("/bank-transfer-qr")
    public ResponseEntity<byte[]> viewBankTransferQr() {
        // QR enabled check — don't serve if the admin turned it off
        String enabled = siteSettingService.getSetting("bank_transfer_qr_enabled");
        if (!"true".equalsIgnoreCase(enabled)) {
            return ResponseEntity.notFound().build();
        }
        return serveSiteAsset("bank_transfer_qr_image");
    }

    private ResponseEntity<byte[]> serveSiteAsset(String settingKey) {
        String path = siteSettingService.getSetting(settingKey);
        if (path == null || path.isEmpty() || path.contains("..") || path.contains("~")) {
            return ResponseEntity.notFound().build();
        }
        try (java.io.InputStream is = photoStorageService.openPhotoStream(path)) {
            byte[] bytes = is.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            // Detect content type from extension
            String ct = path.endsWith(".svg") ? "image/svg+xml"
                    : path.endsWith(".ico") ? "image/x-icon"
                    : path.endsWith(".png") ? "image/png"
                    : path.endsWith(".webp") ? "image/webp"
                    : "image/png";
            headers.setContentType(MediaType.parseMediaType(ct));
            headers.setContentLength(bytes.length);
            headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic());
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
