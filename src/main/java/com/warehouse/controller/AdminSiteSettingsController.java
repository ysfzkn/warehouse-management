package com.warehouse.controller;

import com.warehouse.entity.SiteSetting;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.PaymentConfigService;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.util.CurrentUser;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSiteSettingsController {

    private final SiteSettingService siteSettingService;
    private final PhotoStorageService photoStorageService;
    private final PaymentConfigService paymentConfigService;
    private final AdminSecurityService adminSecurityService;

    public AdminSiteSettingsController(SiteSettingService siteSettingService,
                                       PhotoStorageService photoStorageService,
                                       PaymentConfigService paymentConfigService,
                                       AdminSecurityService adminSecurityService) {
        this.siteSettingService = siteSettingService;
        this.photoStorageService = photoStorageService;
        this.paymentConfigService = paymentConfigService;
        this.adminSecurityService = adminSecurityService;
    }

    // ==================== Site Settings ====================

    @GetMapping("/site")
    public ResponseEntity<List<SiteSetting>> getAll() {
        return ResponseEntity.ok(siteSettingService.getAllSettingEntities());
    }

    @PutMapping("/site")
    public ResponseEntity<Map<String, String>> update(
            @RequestBody(required = false) Map<String, String> settings,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        if (settings == null || settings.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Güncellenecek ayar yok."));
        }
        try {
            siteSettingService.updateSettings(settings, CurrentUser.usernameOrSystem());
        } catch (IllegalStateException ex) {
            // Tek setting'in patlaması — açıklayıcı mesaj döndür
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("message", "Ayarlar güncellendi."));
    }

    // ==================== Logo Upload ====================

    @PostMapping("/site/logo")
    public ResponseEntity<Map<String, String>> uploadLogo(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        try {
            PhotoStorageService.StoredPhoto stored = photoStorageService.storeSiteAsset(
                    "logo",
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream()
            );
            String logoUrl = "/api/store/settings/logo";
            siteSettingService.updateSettings(
                    Map.of("site_logo", stored.relativePath(), "site_logo_url", logoUrl),
                    CurrentUser.usernameOrSystem()
            );
            return ResponseEntity.ok(Map.of("url", logoUrl, "message", "Logo başarıyla yüklendi."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Logo yüklenemedi: " + e.getMessage()));
        }
    }

    @PostMapping("/site/favicon")
    public ResponseEntity<Map<String, String>> uploadFavicon(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        try {
            PhotoStorageService.StoredPhoto stored = photoStorageService.storeSiteAsset(
                    "favicon",
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream()
            );
            String faviconUrl = "/api/store/settings/favicon";
            siteSettingService.updateSettings(
                    Map.of("site_favicon", stored.relativePath(), "site_favicon_url", faviconUrl),
                    CurrentUser.usernameOrSystem()
            );
            return ResponseEntity.ok(Map.of("url", faviconUrl, "message", "Favicon başarıyla yüklendi."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Favicon yüklenemedi: " + e.getMessage()));
        }
    }

    /** Generic asset upload — stores file and returns serve URL. Used for banner images, etc. */
    @PostMapping("/site/upload")
    public ResponseEntity<Map<String, String>> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", defaultValue = "asset") String assetName) {
        try {
            PhotoStorageService.StoredPhoto stored = photoStorageService.storeSiteAsset(
                    assetName,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream()
            );
            // Store the filename only (not full path) — view endpoint resolves it
            String fileName = stored.fileName();
            String viewUrl = "/api/admin/settings/site/asset/view/" + fileName;
            return ResponseEntity.ok(Map.of("url", viewUrl, "path", stored.relativePath(), "fileName", fileName));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dosya yüklenemedi: " + e.getMessage()));
        }
    }

    /** Serve uploaded site assets (banners, etc.) by filename — PUBLIC access */
    @GetMapping("/site/asset/view/{fileName:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> viewAsset(@PathVariable String fileName) {
        try {
            if (fileName == null || fileName.isEmpty() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            // Try multiple locations to find the file
            java.nio.file.Path filePath = null;
            // 1. Via PhotoStorageService
            try {
                java.nio.file.Path siteDir = photoStorageService.getSiteAssetDir();
                java.nio.file.Path candidate = siteDir.resolve(fileName);
                if (java.nio.file.Files.exists(candidate)) filePath = candidate;
            } catch (Exception ignored) {}
            // 2. Fallback: uploads/site relative to CWD
            if (filePath == null) {
                java.nio.file.Path candidate = java.nio.file.Paths.get(System.getProperty("user.dir", ".")).resolve("uploads").resolve("site").resolve(fileName);
                if (java.nio.file.Files.exists(candidate)) filePath = candidate;
            }
            // 3. Fallback: site/ relative to CWD
            if (filePath == null) {
                java.nio.file.Path candidate = java.nio.file.Paths.get(System.getProperty("user.dir", ".")).resolve("site").resolve(fileName);
                if (java.nio.file.Files.exists(candidate)) filePath = candidate;
            }
            if (filePath == null || !java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
            HttpHeaders headers = new HttpHeaders();
            String lp = fileName.toLowerCase();
            String ct = lp.endsWith(".svg") ? "image/svg+xml" : lp.endsWith(".png") ? "image/png" : lp.endsWith(".webp") ? "image/webp" : lp.endsWith(".gif") ? "image/gif" : "image/jpeg";
            headers.setContentType(MediaType.parseMediaType(ct));
            headers.setContentLength(bytes.length);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/site/logo/view")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> viewLogo(@RequestParam("path") String path) {
        try {
            if (path.contains("..") || path.contains("~")) {
                return ResponseEntity.badRequest().build();
            }
            try (java.io.InputStream is = photoStorageService.openPhotoStream(path)) {
                byte[] bytes = is.readAllBytes();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                headers.setContentLength(bytes.length);
                headers.setCacheControl("public, max-age=86400");
                return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Payment Config ====================

    @GetMapping("/payment/bank-transfer")
    public ResponseEntity<Map<String, String>> getBankTransferConfig() {
        return ResponseEntity.ok(paymentConfigService.getBankTransferConfig());
    }

    @PutMapping("/payment/bank-transfer")
    public ResponseEntity<Map<String, String>> updateBankTransferConfig(
            @RequestBody Map<String, String> config,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        paymentConfigService.updateBankTransferConfig(config, CurrentUser.usernameOrSystem());
        return ResponseEntity.ok(Map.of("message", "Banka havalesi ayarları güncellendi."));
    }

    @GetMapping("/payment/iyzico")
    public ResponseEntity<Map<String, String>> getIyzicoConfig() {
        return ResponseEntity.ok(paymentConfigService.getIyzicoConfig());
    }

    @PutMapping("/payment/iyzico")
    public ResponseEntity<Map<String, String>> updateIyzicoConfig(
            @RequestBody Map<String, String> config,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        paymentConfigService.updateIyzicoConfig(config, CurrentUser.usernameOrSystem());
        return ResponseEntity.ok(Map.of("message", "iyzico ayarları güncellendi."));
    }
}
