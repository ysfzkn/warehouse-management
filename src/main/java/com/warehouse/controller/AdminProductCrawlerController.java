package com.warehouse.controller;

import com.warehouse.service.crawler.ProductImageCrawlerService;
import com.warehouse.service.crawler.ProductImageCrawlerService.CrawlException;
import com.warehouse.service.crawler.ProductImageCrawlerService.CrawlPreview;
import com.warehouse.service.crawler.ProductImageCrawlerService.ImageDownload;
import com.warehouse.service.crawler.ProductImageCrawlerService.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin: üçüncü-taraf ürün sayfasından (örn. Profilo, Siemens) ürün fotoğraflarını
 * otomatik çekme/yükleme.
 *
 * <p>İki adımlı UX (admin onayı şart):
 * <ol>
 *   <li>{@code POST /api/admin/products/{id}/crawl-images/preview} — URL'den aday
 *       görselleri listeler (henüz yüklemez)</li>
 *   <li>{@code POST /api/admin/products/{id}/crawl-images/import} — admin'in seçtiği
 *       URL'leri indirir, ürünü güncelleyip ProductImage kayıtlarını oluşturur</li>
 * </ol>
 *
 * <p>Çağrı başına basit rate-limit: aynı admin saniyede 1'den fazla preview/import
 * yapamaz (DoS / 3rd-party rate-limit'ten korur).
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductCrawlerController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductCrawlerController.class);

    private final ProductImageCrawlerService crawler;
    private final ConcurrentHashMap<String, Long> rateLimitWindow = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_WINDOW_MS = 1000;

    public AdminProductCrawlerController(ProductImageCrawlerService crawler) {
        this.crawler = crawler;
    }

    @PostMapping("/{id}/crawl-images/preview")
    public ResponseEntity<?> preview(@PathVariable Long id,
                                      @RequestBody PreviewRequest req) {
        if (rateLimited("preview-" + id)) {
            return ResponseEntity.status(429).body(Map.of("message", "Çok hızlı istek — bir saniye bekleyin"));
        }
        try {
            CrawlPreview p = crawler.preview(req.url);
            log.info("[Crawler] preview productId={} url={} → {} images, desc={} chars",
                    id, req.url, p.images().size(),
                    p.description() != null ? p.description().length() : 0);
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("url", p.url());
            body.put("title", p.title() != null ? p.title() : "");
            body.put("images", p.images());
            body.put("description", p.description());
            body.put("shortDescription", p.shortDescription());
            body.put("specs", p.specs());
            body.put("brand", p.brand());
            return ResponseEntity.ok(body);
        } catch (CrawlException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[Crawler] preview unexpected error", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Beklenmedik hata: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/crawl-images/import")
    public ResponseEntity<?> importImages(@PathVariable Long id,
                                           @RequestBody ImportRequest req) {
        if (rateLimited("import-" + id)) {
            return ResponseEntity.status(429).body(Map.of("message", "Çok hızlı istek — bir saniye bekleyin"));
        }
        if (req == null || req.imageUrls == null || req.imageUrls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "imageUrls boş olamaz"));
        }
        try {
            ImportResult result = crawler.importImages(
                    id,
                    req.imageUrls,
                    Boolean.TRUE.equals(req.replaceExisting),
                    Boolean.TRUE.equals(req.markFirstAsPrimary),
                    req.pageUrl
            );
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "total", result.total(),
                    "errors", result.errors()
            ));
        } catch (CrawlException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[Crawler] import unexpected error", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Beklenmedik hata: " + e.getMessage()));
        }
    }

    /**
     * Hotlink korumalı CDN görselleri için proxy: admin UI thumbnail'larında
     * "HOTLINK IMAGE NOT FOUND" placeholder'ı yerine gerçek görseli göstermek için kullanılır.
     * Backend Referer header'ını ekler, browser yapamadığı için.
     */
    @GetMapping("/crawl-images/proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url,
                                              @RequestParam(value = "referer", required = false) String referer) {
        try {
            ImageDownload dl = crawler.proxyImage(url, referer);
            if (dl == null || dl.bytes() == null || dl.bytes().length == 0) {
                return ResponseEntity.notFound().build();
            }
            String ct = dl.contentType() != null ? dl.contentType() : "image/jpeg";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, ct)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(dl.bytes());
        } catch (Exception e) {
            log.warn("[Crawler] proxy image fail: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /** Saniyede 1 istek sınırı (basit, in-memory). */
    private boolean rateLimited(String key) {
        long now = System.currentTimeMillis();
        Long last = rateLimitWindow.put(key, now);
        return last != null && (now - last) < RATE_LIMIT_WINDOW_MS;
    }

    // ── DTO'lar ──
    public static class PreviewRequest {
        public String url;
    }
    public static class ImportRequest {
        public List<String> imageUrls;
        public Boolean replaceExisting;
        public Boolean markFirstAsPrimary;
        /** Hotlink korumalı CDN'ler (WitCDN/Fakir vb.) için Referer olarak kullanılacak orijinal sayfa URL'i. */
        public String pageUrl;
    }
}
