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
 * Admin: automatically fetch/upload product photos from a third-party product page
 * (e.g. Profilo, Siemens).
 *
 * <p>Two-step UX (admin approval required):
 * <ol>
 *   <li>{@code POST /api/admin/products/{id}/crawl-images/preview} — lists candidate
 *       images from the URL (does not upload yet)</li>
 *   <li>{@code POST /api/admin/products/{id}/crawl-images/import} — downloads the URLs
 *       selected by the admin, updates the product, and creates ProductImage records</li>
 * </ol>
 *
 * <p>Simple per-call rate limit: the same admin cannot run more than 1 preview/import
 * per second (protects against DoS / third-party rate limits).
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductCrawlerController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductCrawlerController.class);

    private final ProductImageCrawlerService crawler;
    private final com.warehouse.service.crawler.ProductCrawlBatchService batchCrawler;
    private final ConcurrentHashMap<String, Long> rateLimitWindow = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_WINDOW_MS = 1000;

    public AdminProductCrawlerController(ProductImageCrawlerService crawler,
                                          com.warehouse.service.crawler.ProductCrawlBatchService batchCrawler) {
        this.crawler = crawler;
        this.batchCrawler = batchCrawler;
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
            // Grouped specs ({title, items:[{label,value}]}) — the admin form prefers
            // these so the page's sections (Genel özellikler, Boyutlar…) are preserved.
            body.put("specGroups", p.specGroups());
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
     * Proxy for hotlink-protected CDN images: used to display the real image instead of
     * the "HOTLINK IMAGE NOT FOUND" placeholder in admin UI thumbnails.
     * The backend adds the Referer header, since the browser cannot.
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

    // ─────────────────────────────────────────────────────────────
    //  Bulk import: paste many URLs, match them to products, apply
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the crawl-and-match run. Returns a job id rather than the result: every URL
     * has to be fetched before it can be matched, and a long list would outlive any
     * proxy timeout. The client polls {@link #batchStatus}.
     */
    @PostMapping("/crawl-images/batch/match")
    public ResponseEntity<?> batchMatch(@RequestBody BatchMatchRequest req) {
        try {
            String jobId = batchCrawler.startMatch(req == null ? null : req.urls);
            return ResponseEntity.ok(Map.of("jobId", jobId));
        } catch (CrawlException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[CrawlBatch] match start failed", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Beklenmedik hata: " + e.getMessage()));
        }
    }

    /** The supplier hosts the crawler will read, so the UI can list them once. */
    @GetMapping("/crawl-images/supported-hosts")
    public ResponseEntity<?> supportedHosts() {
        return ResponseEntity.ok(Map.of("hosts", ProductImageCrawlerService.allowedHosts()));
    }

    /** Progress and per-row results for a running or finished batch. */
    @GetMapping("/crawl-images/batch/{jobId}")
    public ResponseEntity<?> batchStatus(@PathVariable String jobId) {
        try {
            var job = batchCrawler.getJob(jobId);
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (var item : job.items) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("url", item.url);
                row.put("status", item.status);
                row.put("message", item.message);
                row.put("title", item.title);
                row.put("brand", item.brand);
                row.put("productId", item.productId);
                row.put("imageCount", item.images.size());
                // First image only: the confirmation list shows a thumbnail, not a gallery.
                row.put("thumbnail", item.images.isEmpty() ? null : item.images.get(0));
                row.put("hasDescription", item.description != null && !item.description.isBlank());
                row.put("specGroupCount", item.specGroups == null ? 0 : item.specGroups.size());
                row.put("candidates", item.candidates);
                items.add(row);
            }
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("jobId", job.id);
            body.put("state", job.state);
            body.put("error", job.error);
            body.put("total", job.total());
            body.put("processed", job.processed.get());
            body.put("items", items);
            return ResponseEntity.ok(body);
        } catch (CrawlException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Applies the rows the admin confirmed: photos appended, copy and specs written. */
    @PostMapping("/crawl-images/batch/{jobId}/apply")
    public ResponseEntity<?> batchApply(@PathVariable String jobId,
                                         @RequestBody BatchApplyRequest req) {
        try {
            var summary = batchCrawler.apply(jobId, req == null ? null : req.items);
            log.info("[CrawlBatch] applied job={} products={} photos={} errors={}",
                    jobId, summary.applied(), summary.photos(), summary.errors().size());
            return ResponseEntity.ok(Map.of(
                    "applied", summary.applied(),
                    "photos", summary.photos(),
                    "errors", summary.errors()
            ));
        } catch (CrawlException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[CrawlBatch] apply unexpected error", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Beklenmedik hata: " + e.getMessage()));
        }
    }

    /** 1-request-per-second limit (simple, in-memory). */
    private boolean rateLimited(String key) {
        long now = System.currentTimeMillis();
        Long last = rateLimitWindow.put(key, now);
        return last != null && (now - last) < RATE_LIMIT_WINDOW_MS;
    }

    // ── DTOs ──
    public static class PreviewRequest {
        public String url;
    }
    public static class ImportRequest {
        public List<String> imageUrls;
        public Boolean replaceExisting;
        public Boolean markFirstAsPrimary;
        /** Original page URL to use as the Referer for hotlink-protected CDNs (WitCDN/Fakir etc.). */
        public String pageUrl;
    }
    public static class BatchMatchRequest {
        /** Raw pasted text or one entry per link; both are split server-side. */
        public List<String> urls;
    }
    public static class BatchApplyRequest {
        public List<com.warehouse.service.crawler.ProductCrawlBatchService.ApplyRequestItem> items;
    }
}
