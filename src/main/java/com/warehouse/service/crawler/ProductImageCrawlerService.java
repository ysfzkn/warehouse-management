package com.warehouse.service.crawler;

import com.warehouse.security.SsrfGuard;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import com.warehouse.repository.ProductImageRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.ProductImageService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically fetches product photos from a third-party product page
 * (e.g. Profilo) and uploads them to the local system.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@link #preview(String)} — validates the URL, fetches the HTML, and
 *       extracts candidate images (og:image, JSON-LD, &lt;img&gt; gallery, srcset).
 *       Returns a preview list to the UI.</li>
 *   <li>{@link #importImages} — downloads the URLs approved by the admin one by
 *       one, validates size/MIME, writes them to disk via {@link PhotoStorageService},
 *       and inserts rows into the {@code product_images} table.</li>
 * </ol>
 *
 * <h3>Edge cases</h3>
 * <ul>
 *   <li><b>SSRF protection</b> — HTTPS only, host allowlist, private/loopback IP rejection</li>
 *   <li><b>Rate limit</b> — 5-second cooldown (user cannot fire calls back to back)</li>
 *   <li><b>Timeout</b> — fetch 15s, image download 10s</li>
 *   <li><b>File size</b> — max 10 MB per image, max 20 images per page</li>
 *   <li><b>Format</b> — JPG/PNG/WebP/AVIF only; magic-byte validation</li>
 *   <li><b>Duplicates</b> — the same URL appears only once; order preserved via LinkedHashSet</li>
 *   <li><b>Lazy-load</b> — data-src, data-original, and srcset are all tried</li>
 *   <li><b>Thumbnail filter</b> — "thumb"/"icon"/"logo"/"sprite" path patterns are dropped</li>
 *   <li><b>HTML changes</b> — multi-strategy: og:image → JSON-LD → DOM. Profilo-specific
 *       selectors come first; if they disappear, a generic fallback kicks in.</li>
 *   <li><b>Duplicate signature</b> — the admin may tick "Delete existing images"; defaults to append mode</li>
 * </ul>
 */
@Service
public class ProductImageCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageCrawlerService.class);

    /**
     * Allowed hosts — SSRF / abuse protection. Add new vendors here.
     * Both ".com" and ".com.tr" variants (the Turkish and global versions may differ).
     * Subdomains (m.profilo.com, www.profilo.com, etc.) are accepted automatically via
     * the {@code endsWith("." + host)} check in {@link #validateUrl}.
     */
    private static final List<String> ALLOWED_HOSTS = List.of(
            "profilo.com",          "profilo.com.tr",
            "siemens-home.bsh-group.com", "siemens.com.tr", "siemens-home.com.tr",
            "bosch-home.com",       "bosch-home.com.tr",
            "lg.com",               "lg.com.tr",
            "miele.com",            "miele.com.tr",
            // haier.com.tr removed: the domain now serves an unrelated site.
            "fakir.com.tr",         "fakir.com",
            // Merchant's active supplier brands:
            "simfer.com.tr",        "simfer.com",
            "ferreturkiye.com",
            "kaercher.com",
            "kumtel.com",
            "tefal.com.tr",         "tefal.com",
            "braunshop.com.tr",
            "philips.com.tr",       "philips.com",
            "rotaclimate.com"
            // Removed (not needed): arcelik, beko, vestel, samsung.
            // NOTE: altus.com.tr does TLS fingerprinting with Akamai Bot Manager;
            // it cannot be bypassed with server-side HTTP clients (Jsoup/HttpURLConnection).
    );

    private static final int MAX_IMAGES = 20;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final int FETCH_TIMEOUT_MS = 15_000;
    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 10_000;
    // Realistic Chrome UA — the "compatible; XxxBot" pattern is automatically
    // blocked by bot managers such as Akamai/Cloudflare (e.g. Altus.com.tr).
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final PhotoStorageService photoStorageService;
    private final ProductImageService productImageService;

    public ProductImageCrawlerService(ProductRepository productRepository,
                                       ProductImageRepository productImageRepository,
                                       PhotoStorageService photoStorageService,
                                       ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.photoStorageService = photoStorageService;
        this.productImageService = productImageService;
    }

    // ─────────────────────────────────────────────────────────────
    //  Preview
    // ─────────────────────────────────────────────────────────────

    /** Validate the URL, fetch the HTML, and return candidate image URLs (for the admin to preview). */
    public CrawlPreview preview(String pageUrl) {
        validateUrl(pageUrl);
        long t0 = System.currentTimeMillis();
        Document doc = fetchDocument(pageUrl);

        // Each field is extracted defensively: a flaky description/spec parser for one
        // site must never sink the whole preview — above all, the IMAGES (the point of
        // this feature) must still come through.
        String title = safeExtract(() -> firstNonBlank(
                doc.select("meta[property=og:title]").attr("content"), doc.title()), "", "title");
        List<String> images = safeExtract(() -> extractImageUrls(doc, pageUrl),
                java.util.List.of(), "images");
        String description = safeExtract(() -> extractDescription(doc), null, "description");
        String shortDescription = safeExtract(() -> extractShortDescription(doc, description),
                null, "shortDescription");
        // Grouped specs first (sections preserved, e.g. BSH "Genel özellikler" / "Boyutlar");
        // the flat map is derived from them. Flat extraction is the fallback.
        List<SpecGroup> specGroups = safeExtract(() -> extractSpecGroups(doc),
                java.util.List.of(), "specGroups");
        java.util.Map<String, String> specs;
        if (!specGroups.isEmpty()) {
            java.util.Map<String, String> derived = new java.util.LinkedHashMap<>();
            for (SpecGroup g : specGroups) {
                for (SpecItem it : g.items()) derived.putIfAbsent(it.label(), it.value());
            }
            specs = derived;
        } else {
            specs = safeExtract(() -> extractSpecs(doc), java.util.Map.of(), "specs");
        }
        String brand = safeExtract(() -> extractBrand(doc), null, "brand");

        long ms = System.currentTimeMillis() - t0;
        log.info("[Crawler] preview {} → {} images, desc={} chars, specs={} in {} groups ({}ms)",
                pageUrl, images.size(),
                description != null ? description.length() : 0,
                specs.size(), specGroups.size(), ms);
        return new CrawlPreview(pageUrl, title.trim(), images,
                description, shortDescription, specs, specGroups, brand, null);
    }

    /**
     * Runs one extraction step, returning a fallback (and logging) if it throws — so a
     * single fragile field parser can't crash the whole crawl request.
     */
    private <T> T safeExtract(java.util.function.Supplier<T> extractor, T fallback, String field) {
        try {
            T value = extractor.get();
            return value != null ? value : fallback;
        } catch (Exception | StackOverflowError e) {
            // StackOverflowError (not an Exception) can come from a pathological regex
            // on a specific site's markup — catch it here so one field can't 500 the crawl.
            log.warn("[Crawler] '{}' çıkarımı atlandı: {}", field, e.toString());
            return fallback;
        }
    }

    /**
     * Fetches the page with explicit HTTP-status handling and a single retry for
     * transient upstream failures (nginx "currently unavailable" 5xx, timeouts).
     * Turns error responses into short, actionable Turkish messages instead of
     * dumping the raw error HTML back to the admin.
     */
    private Document fetchDocument(String pageUrl) {
        final int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Redirects are followed by hand so every hop passes the SSRF check.
                // With followRedirects(true) only the first URL was ever validated, and a
                // supplier page answering "302 -> http://169.254.169.254/" would have been
                // fetched from inside the network without a second look.
                java.net.URI current = SsrfGuard.validate(pageUrl);
                org.jsoup.Connection.Response resp = null;
                int code = 0;
                for (int hop = 0; hop <= SsrfGuard.MAX_REDIRECTS; hop++) {
                    resp = Jsoup.connect(current.toString())
                            .userAgent(USER_AGENT)
                            .timeout(FETCH_TIMEOUT_MS)
                            .followRedirects(false)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                            .ignoreHttpErrors(true) // inspect the status ourselves instead of throwing raw
                            .execute();
                    code = resp.statusCode();
                    if (code < 300 || code >= 400) break;
                    String location = resp.header("Location");
                    if (location == null || location.isBlank()) break;
                    if (hop == SsrfGuard.MAX_REDIRECTS) {
                        throw new CrawlException("Çok fazla yönlendirme.");
                    }
                    current = SsrfGuard.validateRedirect(current, location);
                }
                if (code == 404 || code == 410) {
                    throw new CrawlException("Ürün sayfası bulunamadı (" + code
                            + "). URL eksik veya hatalı olabilir — tarayıcıdan açıp tam adresi kopyalayın.");
                }
                if (code == 429) {
                    throw new CrawlException("Site çok fazla istek aldı (429). Lütfen biraz bekleyip tekrar deneyin.");
                }
                if (code >= 500) {
                    if (attempt < maxAttempts) { sleepQuietly(1200); continue; }
                    throw new CrawlException("Site şu an yanıt vermiyor (" + code
                            + "). Birkaç saniye sonra tekrar deneyin.");
                }
                if (code < 200 || code >= 300) {
                    throw new CrawlException("Sayfa erişilemez (" + code + ").");
                }
                Document doc = resp.parse();
                if (looksLikeErrorPage(doc)) {
                    if (attempt < maxAttempts) { sleepQuietly(1200); continue; }
                    throw new CrawlException("Sayfa bir hata döndürdü. Ürün geçici olarak erişilemiyor olabilir "
                            + "veya URL geçersiz — kontrol edip tekrar deneyin.");
                }
                return doc;
            } catch (java.net.SocketTimeoutException e) {
                if (attempt < maxAttempts) { sleepQuietly(1000); continue; }
                throw new CrawlException("Sayfa yanıt vermedi (zaman aşımı). Tekrar deneyin.", e);
            } catch (CrawlException e) {
                throw e;
            } catch (java.io.IOException e) {
                if (attempt < maxAttempts) { sleepQuietly(1000); continue; }
                throw new CrawlException("Sayfaya bağlanılamadı. Bağlantıyı kontrol edip tekrar deneyin.", e);
            }
        }
        throw new CrawlException("Sayfa çekilemedi."); // unreachable; keeps the compiler happy
    }

    /** Heuristic: a server/error placeholder page rather than a real product page. */
    private boolean looksLikeErrorPage(Document doc) {
        if (doc == null) return true;
        String title = doc.title() == null ? "" : doc.title().trim().toLowerCase();
        if (title.equals("error") || title.startsWith("error ")
                || title.startsWith("error-") || title.startsWith("error—")) {
            return true;
        }
        if (!doc.select("html#__next_error__, #__next_error__").isEmpty()) {
            return true;
        }
        String body = doc.body() != null ? doc.body().text().toLowerCase() : "";
        if (body.isEmpty()) return true;
        boolean nginxError = body.contains("an error occurred")
                && (body.contains("nginx") || body.contains("try again later"));
        boolean unavailable = body.contains("currently unavailable") && body.contains("try again later");
        return nginxError || unavailable;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Import
    // ─────────────────────────────────────────────────────────────

    /**
     * Downloads the selected URLs and saves them for the product.
     *
     * @param productId target product
     * @param imageUrls image URLs to download (selected from those found in the preview)
     * @param replaceExisting if true, the product's existing images are deleted
     * @param markFirstAsPrimary if true, the first successfully uploaded image is marked "primary"
     */
    public ImportResult importImages(Long productId,
                                      List<String> imageUrls,
                                      boolean replaceExisting,
                                      boolean markFirstAsPrimary) {
        return importImages(productId, imageUrls, replaceExisting, markFirstAsPrimary, null);
    }

    /** Referer-aware variant — the page URL is passed for CDNs with hotlink protection (WitCDN/Fakir, etc.). */
    public ImportResult importImages(Long productId,
                                      List<String> imageUrls,
                                      boolean replaceExisting,
                                      boolean markFirstAsPrimary,
                                      String referer) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CrawlException("Ürün bulunamadı: " + productId));

        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ImportResult(0, 0, List.of("İndirilecek görsel seçilmedi"));
        }
        if (imageUrls.size() > MAX_IMAGES) {
            imageUrls = imageUrls.subList(0, MAX_IMAGES);
        }

        // Delete existing images (if requested)
        if (replaceExisting) {
            List<ProductImage> existing = productImageRepository.findByProductOrderBySortOrderAscIdAsc(product);
            for (ProductImage img : existing) {
                try {
                    productImageService.deleteImage(img.getId());
                } catch (Exception e) {
                    log.warn("Mevcut görsel silinemedi (id={}): {}", img.getId(), e.getMessage());
                }
            }
        }

        int success = 0;
        int firstStoredId = -1;
        List<String> errors = new ArrayList<>();

        for (String url : imageUrls) {
            try {
                ImageDownload dl = downloadImage(url, referer);
                if (dl == null) {
                    errors.add(shortenUrl(url) + ": indirilemedi");
                    continue;
                }
                if (dl.bytes.length > MAX_IMAGE_BYTES) {
                    errors.add(shortenUrl(url) + ": dosya çok büyük (" + (dl.bytes.length / 1024 / 1024) + "MB)");
                    continue;
                }
                if (!isValidImageMagic(dl.bytes)) {
                    errors.add(shortenUrl(url) + ": geçerli görsel değil");
                    continue;
                }

                String filename = inferFilename(url, dl.contentType);
                try (InputStream in = new ByteArrayInputStream(dl.bytes)) {
                    var stored = productImageService.addImageToProduct(
                            productId,
                            filename,
                            dl.contentType != null ? dl.contentType : "image/jpeg",
                            in,
                            success == 0 && markFirstAsPrimary
                    );
                    if (firstStoredId < 0) firstStoredId = stored.getId().intValue();
                    success++;
                }
            } catch (Exception e) {
                log.warn("[Crawler] import hatası ({}): {}", shortenUrl(url), e.getMessage());
                errors.add(shortenUrl(url) + ": " + truncate(e.getMessage(), 120));
            }
        }

        log.info("[Crawler] import sonucu: productId={}, başarılı={}/{}, hata={}",
                productId, success, imageUrls.size(), errors.size());
        return new ImportResult(success, imageUrls.size(), errors);
    }

    // ─────────────────────────────────────────────────────────────
    //  Image extraction strategies
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    //  Description / specs extraction
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the long description using 5 different strategies (in priority order):
     * <ol>
     *   <li>schema.org JSON-LD Product.description</li>
     *   <li>meta[itemprop=description]</li>
     *   <li>section.product-description / div.tab-pane (Bosch/Siemens/Beko pattern)</li>
     *   <li>article > div.description / .product-details</li>
     *   <li>og:description (usually short, but a fallback)</li>
     * </ol>
     * Maximum 5000 characters; HTML tags are preserved but sanitized (no script/style).
     */
    String extractDescription(Document doc) {
        // 1) JSON-LD
        try {
            for (var el : doc.select("script[type=application/ld+json]")) {
                String json = el.data();
                if (json == null || json.isBlank()) continue;
                // Match a JSON string body safely: an escape sequence (\\.) OR any char
                // that is NOT a quote or backslash. The two alternatives are mutually
                // exclusive, so this can't catastrophically backtrack. (The old pattern
                // (?:\\"|[^"])* let a backslash match either branch → ReDoS/StackOverflow
                // on long escaped descriptions like fakir.com.tr's.)
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", java.util.regex.Pattern.DOTALL)
                        .matcher(json);
                if (m.find()) {
                    String s = m.group(1)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\/", "/");
                    if (s.length() > 50) return truncateSmart(cleanHtml(s), 5000);
                }
            }
        } catch (Exception ignored) {}

        // 2) Itemprop description
        var ip = doc.selectFirst("[itemprop=description]");
        if (ip != null) {
            String txt = ip.text();
            if (txt != null && txt.length() > 30) return truncateSmart(txt, 5000);
        }

        // 3) Common product detail selectors
        String[] selectors = {
                ".product-description", ".product-details-description",
                ".tab-content .product-description", "#description",
                ".product-info .description", ".product-detail-content",
                "section.description", "div[class*=description]",
                ".product-overview", "#productDescription",
        };
        for (String sel : selectors) {
            var el = doc.selectFirst(sel);
            if (el != null) {
                String html = el.html();
                String txt = el.text();
                if (txt != null && txt.length() > 80) {
                    // Preserve HTML — this shows rich text to the admin
                    return truncateSmart(cleanHtml(html), 5000);
                }
            }
        }

        // 4) Concatenation of all <p> tags (main content area)
        try {
            var paragraphs = doc.select("main p, article p, .product p");
            if (paragraphs.isEmpty()) paragraphs = doc.select("p");
            StringBuilder sb = new StringBuilder();
            for (var p : paragraphs) {
                String t = p.text();
                if (t == null || t.length() < 40) continue;
                // Skip navigation/footer text
                String clsParent = p.parent() != null ? String.valueOf(p.parent().className()) : "";
                if (clsParent.toLowerCase().matches(".*(menu|nav|footer|header|breadcrumb).*")) continue;
                sb.append(t).append("\n\n");
                if (sb.length() > 3000) break;
            }
            if (sb.length() > 100) return truncateSmart(sb.toString().trim(), 5000);
        } catch (Exception ignored) {}

        // 5) og:description fallback (usually short)
        String og = doc.select("meta[property=og:description]").attr("content");
        if (og != null && og.length() > 30) return truncateSmart(og, 5000);

        return null;
    }

    /** Short description — the meta description or the first sentence of the description. */
    String extractShortDescription(Document doc, String longDescription) {
        // 1) meta name="description"
        String meta = doc.select("meta[name=description]").attr("content");
        if (meta != null && meta.length() > 30 && meta.length() < 300) {
            return meta.trim();
        }
        // 2) og:description
        String og = doc.select("meta[property=og:description]").attr("content");
        if (og != null && og.length() > 30 && og.length() < 300) {
            return og.trim();
        }
        // 3) the first 200 characters of the long description
        if (longDescription != null && longDescription.length() > 50) {
            String plain = longDescription.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (plain.length() > 200) return plain.substring(0, 197) + "...";
            return plain;
        }
        return null;
    }

    /**
     * Extracts technical specifications from table/dl/ul structures.
     * E.g. "Capacity: 9 kg", "Energy Class: A++", "Color: White".
     */
    java.util.Map<String, String> extractSpecs(Document doc) {
        java.util.Map<String, String> specs = new java.util.LinkedHashMap<>();
        final int MAX = 50;

        // 1) schema.org PropertyValue (JSON-LD) — highest quality, takes priority.
        try {
            for (var el : doc.select("script[type=application/ld+json]")) {
                String json = el.data();
                if (json == null || json.isBlank()) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\"@type\"\\s*:\\s*\"PropertyValue\"\\s*,[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"value\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(json);
                while (m.find() && specs.size() < MAX) {
                    putSpecRow(specs, m.group(1), m.group(2));
                }
            }
        } catch (Exception ignored) {}

        // 2) Generic: ANY <table> whose rows are 2-cell key/value pairs. Covers
        //    site-specific spec tables with arbitrary classes (e.g. Ferre's
        //    "mytable"). Only tables yielding >= 2 spec-like rows are accepted, so
        //    layout/price tables are skipped.
        try {
            for (var table : doc.select("table")) {
                java.util.Map<String, String> fromTable = new java.util.LinkedHashMap<>();
                for (var row : table.select("tr")) {
                    var cells = row.select("th, td");
                    if (cells.size() == 2) {
                        String k = cleanSpecLabel(cells.get(0).text());
                        String v = cells.get(1).text().trim();
                        if (looksLikeSpec(k, v)) fromTable.put(k, v);
                    }
                }
                if (fromTable.size() >= 2) {
                    for (var e : fromTable.entrySet()) {
                        if (specs.size() >= MAX) break;
                        specs.putIfAbsent(e.getKey(), e.getValue());
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3) <dl> definition lists (any).
        try {
            for (var dl : doc.select("dl")) {
                var dts = dl.select("dt");
                var dds = dl.select("dd");
                int n = Math.min(dts.size(), dds.size());
                for (int i = 0; i < n && specs.size() < MAX; i++) {
                    putSpecRow(specs, dts.get(i).text(), dds.get(i).text());
                }
            }
        } catch (Exception ignored) {}

        // 4) "Label: Value" rows inside spec/detail/özellik containers (list items).
        try {
            for (var li : doc.select(
                    ".specs li, .features li, .product-features li, [class*=spec] li, [class*=ozellik] li,"
                    + " [class*=teknik] li, [class*=detail] li, [class*=attribute] li, [class*=property] li")) {
                String txt = li.text().trim();
                int idx = txt.indexOf(':');
                if (idx > 0 && idx < 70 && idx < txt.length() - 1) {
                    putSpecRow(specs, txt.substring(0, idx), txt.substring(idx + 1));
                }
                if (specs.size() >= MAX) break;
            }
        } catch (Exception ignored) {}

        // 5) Div/tile "item" rows — a label element + a value element/text, with NO colon
        //    and NO table. Covers the BSH platform used by Profilo / Siemens / Bosch
        //    (data-testid="technical-overview-item", feature tiles) and generic modern
        //    spec/attribute/feature rows that the table/dl/colon steps miss.
        try {
            org.jsoup.select.Elements items = doc.select(
                    "[data-testid*=technical-overview-item], [data-testid*=specification-item],"
                  + " [data-testid*=spec-item], [data-testid*=feature-tile], [data-testid*=attribute-item],"
                  + " [class*=spec-row], [class*=specification-row], [class*=attribute-row], [class*=feature-row]");
            for (var item : items) {
                if (specs.size() >= MAX) break;
                String label = null;
                StringBuilder value = new StringBuilder();
                for (var child : item.children()) {
                    String t = child.text().trim();
                    if (t.isEmpty()) continue;
                    if (label == null) {
                        label = t;
                    } else {
                        if (value.length() > 0) value.append(' ');
                        value.append(t);
                    }
                }
                // Single labelled child: the value is the item's own remaining text.
                if (label != null && value.length() == 0) {
                    String full = item.text().trim();
                    if (full.length() > label.length() && full.startsWith(label)) {
                        value.append(full.substring(label.length()).trim());
                    }
                }
                if (label != null && value.length() > 0) {
                    putSpecRow(specs, label, value.toString());
                }
            }
        } catch (Exception ignored) {}

        return specs;
    }

    private static void putSpecRow(java.util.Map<String, String> specs, String rawK, String rawV) {
        String k = cleanSpecLabel(rawK);
        String v = rawV == null ? "" : rawV.trim();
        if (looksLikeSpec(k, v)) specs.putIfAbsent(k, v);
    }

    private static String cleanSpecLabel(String s) {
        if (s == null) return "";
        String k = s.trim();
        if (k.endsWith(":")) k = k.substring(0, k.length() - 1).trim();
        return k;
    }

    /** Commerce/navigation labels that look like 2-cell rows but aren't product specs. */
    private static final java.util.regex.Pattern SPEC_JUNK_KEY = java.util.regex.Pattern.compile(
            "(?i)(price|fiyat|cost|[uü]cret|kdv|vat|tax|taksit|installment|sepet|cart|kargo|"
            + "shipping|stok|stock|menu|login|giri[sş]|payment|[oö]deme|toplam|ara\\s*toplam|indirim)");

    private static boolean looksLikeSpec(String k, String v) {
        if (k == null || v == null || k.isEmpty() || v.isEmpty()) return false;
        if (k.length() < 2 || k.length() > 60 || v.length() > 300) return false;
        String lk = k.toLowerCase();
        if (lk.contains("function") || lk.contains("{") || lk.startsWith("http")) return false;
        if (v.contains("{") || v.contains("</") || v.startsWith("http")) return false;
        // Skip pricing / cart / navigation rows.
        if (SPEC_JUNK_KEY.matcher(k).find()) return false;
        // Skip pure currency amounts (e.g. "1.299,00 TL", "₺99") — but keep plain
        // numeric specs like "350" (no currency marker).
        if (v.matches("(?i)^\\s*[₺$€]?\\s*\\d[\\d.,]*\\s*(tl|try|usd|eur|₺|\\$|€)\\s*$")) return false;
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    //  Grouped technical specs (sections preserved)
    // ─────────────────────────────────────────────────────────────

    private static final int MAX_SPEC_GROUPS = 15;
    private static final int MAX_ITEMS_PER_GROUP = 80;
    private static final int MAX_TOTAL_SPEC_ITEMS = 300;

    private static final com.fasterxml.jackson.databind.ObjectMapper SPEC_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Extracts technical specifications WITH their section grouping, matching the
     * product's structured technicalSpecs shape ({@code [{title, items:[{label,value}]}]}).
     *
     * <p>Strategy order:</p>
     * <ol>
     *   <li>BSH platform (Profilo / Bosch / Siemens) — the full spec list ("Genel
     *       özellikler", "Boyutlar", "Soğutucu bölümü", …) is embedded in the Next.js
     *       App Router flight payload ({@code self.__next_f.push}) as escaped JSON.</li>
     *   <li>Generic: each accepted key/value table becomes a group titled by its
     *       caption or the nearest preceding heading.</li>
     * </ol>
     * Returns an empty list when neither strategy yields results (the flat
     * {@link #extractSpecs} map remains the fallback).
     */
    List<SpecGroup> extractSpecGroups(Document doc) {
        try {
            List<SpecGroup> bsh = parseBshSpecGroups(collectFlightPayload(doc));
            if (!bsh.isEmpty()) return bsh;
        } catch (Exception e) {
            log.debug("[Crawler] BSH spec-group parse failed: {}", e.getMessage());
        }
        try {
            List<SpecGroup> tables = extractTableSpecGroups(doc);
            if (!tables.isEmpty()) return tables;
        } catch (Exception e) {
            log.debug("[Crawler] table spec-group parse failed: {}", e.getMessage());
        }
        try {
            // <dl> definition lists with per-section headings (e.g. Philips:
            // "Menşei" / "Teknik Özellikler" / "Aksesuarlar" titles above each list).
            List<SpecGroup> dls = extractDlSpecGroups(doc);
            if (!dls.isEmpty()) return dls;
        } catch (Exception e) {
            log.debug("[Crawler] dl spec-group parse failed: {}", e.getMessage());
        }
        try {
            // <ol>/<ul> lists whose items carry an emphasized label followed by the
            // value (e.g. Miele: <li><em>Maks. Watt gücü<br></em>890</li>), grouped
            // by the preceding bolded paragraph or heading ("Teknik veriler").
            return extractLabeledListSpecGroups(doc);
        } catch (Exception e) {
            log.debug("[Crawler] labeled-list spec-group parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Concatenates the contents of all {@code self.__next_f.push([n,"..."])} chunks in
     * document order and unescapes them once, reconstructing the streamed flight text.
     * Chunk boundaries can split JSON mid-token, so concatenation must happen on the
     * still-escaped payloads BEFORE unescaping.
     *
     * <p>Hand-rolled scanner on purpose: a regex with an escaped-string alternation
     * recurses per character in Java's engine and overflows the stack on real BSH
     * payloads (hundreds of KB in a single script tag).</p>
     */
    String collectFlightPayload(Document doc) {
        final String PUSH = "self.__next_f.push([";
        StringBuilder escaped = new StringBuilder();
        for (Element s : doc.select("script")) {
            String data = s.data();
            if (data == null || !data.contains("self.__next_f")) continue;
            int idx = 0;
            while ((idx = data.indexOf(PUSH, idx)) >= 0) {
                int p = idx + PUSH.length();
                while (p < data.length() && Character.isDigit(data.charAt(p))) p++;
                if (p + 1 < data.length() && data.charAt(p) == ',' && data.charAt(p + 1) == '"') {
                    int i = p + 2;
                    int start = i;
                    while (i < data.length()) {
                        char c = data.charAt(i);
                        if (c == '\\') { i += 2; continue; }   // skip escaped char
                        if (c == '"') break;                    // unescaped close quote
                        i++;
                    }
                    if (i > data.length()) i = data.length();
                    escaped.append(data, start, Math.min(i, data.length()));
                    idx = Math.min(i + 1, data.length());
                } else {
                    idx = p; // push([0]) etc. — no string payload
                }
            }
        }
        if (escaped.length() == 0) return "";
        return unescapeJsString(escaped.toString());
    }

    /**
     * Finds the BSH spec-group array in the (unescaped) flight text and maps it to
     * {@link SpecGroup}s. The outer group array is recognizable as
     * {@code "specifications":[{"name":"<plain string>"} — inner per-group item arrays
     * start with {@code [{"key":"…"}} instead, so the marker cannot match them.
     */
    List<SpecGroup> parseBshSpecGroups(String flightText) {
        if (flightText == null || flightText.isEmpty()) return List.of();
        String marker = "\"specifications\":[{\"name\":\"";
        int from = 0;
        while ((from = flightText.indexOf(marker, from)) >= 0) {
            int arrayStart = flightText.indexOf('[', from);
            String jsonArray = extractBalancedArray(flightText, arrayStart);
            if (jsonArray != null) {
                List<SpecGroup> groups = mapBshGroups(jsonArray);
                if (!groups.isEmpty()) return groups;
            }
            from += marker.length();
        }
        return List.of();
    }

    /** Parses the extracted JSON array and maps BSH group/spec objects to SpecGroups. */
    private List<SpecGroup> mapBshGroups(String jsonArray) {
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = SPEC_JSON.readTree(jsonArray);
        } catch (Exception e) {
            return List.of();
        }
        if (root == null || !root.isArray()) return List.of();

        List<SpecGroup> groups = new ArrayList<>();
        int total = 0;
        for (com.fasterxml.jackson.databind.JsonNode g : root) {
            if (groups.size() >= MAX_SPEC_GROUPS || total >= MAX_TOTAL_SPEC_ITEMS) break;
            String title = g.path("name").isTextual() ? g.path("name").asText().trim() : null;
            com.fasterxml.jackson.databind.JsonNode specsNode = g.path("specifications");
            if (title == null || title.isEmpty() || title.length() > 80 || !specsNode.isArray()) continue;

            List<SpecItem> items = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode spec : specsNode) {
                if (items.size() >= MAX_ITEMS_PER_GROUP || total + items.size() >= MAX_TOTAL_SPEC_ITEMS) break;
                String label = textOf(spec.path("name"));
                String value = textOf(spec.path("value"));
                if (label == null || value == null) continue;
                value = resolveTranslatedValue(value, spec.path("requiresValueTranslation").asBoolean(false));
                if (value == null) continue;
                String unit = spec.path("unit").isTextual() ? spec.path("unit").asText().trim() : "";
                if (!unit.isEmpty()) value = value + " " + unit;
                label = cleanSpecLabel(label);
                if (looksLikeSpec(label, value)) items.add(new SpecItem(label, value.trim()));
            }
            if (!items.isEmpty()) {
                groups.add(new SpecGroup(title, items));
                total += items.size();
            }
        }
        return groups;
    }

    /** BSH text node: either {"footnoteDataArray":[],"text":"…"} or a plain string. */
    private static String textOf(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.isTextual() ? node.asText() : node.path("text").asText(null);
        if (t == null) return null;
        t = t.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Dotted i18n keys like "an.yes" or "an.someFeatureKey". Must start with a
     * lowercase letter so dotted numerics ("1.5") are never mistaken for keys.
     */
    private static final Pattern TRANSLATION_KEY =
            Pattern.compile("^[a-z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$");

    /**
     * Resolves i18n-key values ({@code requiresValueTranslation:true}) to Turkish.
     * Returns null when the value would remain a raw key (e.g. "an.someFeature") —
     * raw keys must never reach the storefront.
     */
    private static String resolveTranslatedValue(String value, boolean requiresTranslation) {
        String v = value.trim();
        if (requiresTranslation) {
            String last = v.substring(v.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            switch (last) {
                case "yes", "true": return "Evet";
                case "no", "false": return "Hayır";
                default: break;
            }
        }
        return TRANSLATION_KEY.matcher(v).matches() ? null : v;
    }

    /**
     * Generic grouped fallback: every accepted 2-cell key/value table becomes one
     * group, titled by its {@code <caption>} or the nearest preceding heading.
     */
    private List<SpecGroup> extractTableSpecGroups(Document doc) {
        List<SpecGroup> groups = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>(); // mobile+desktop markup repeats the same table
        int total = 0;
        for (Element table : doc.select("table")) {
            if (groups.size() >= MAX_SPEC_GROUPS || total >= MAX_TOTAL_SPEC_ITEMS) break;
            List<SpecItem> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Element row : table.select("tr")) {
                if (items.size() >= MAX_ITEMS_PER_GROUP) break;
                var cells = row.select("th, td");
                if (cells.size() != 2) continue;
                String k = cleanSpecLabel(cells.get(0).text());
                String v = cells.get(1).text().trim();
                if (looksLikeSpec(k, v) && seen.add(k.toLowerCase(Locale.ROOT))) {
                    items.add(new SpecItem(k, v));
                }
            }
            // Same acceptance bar as the flat extractor: layout/price tables are skipped.
            if (items.size() < 2) continue;
            String title = tableGroupTitle(table);
            String signature = title + "|" + items;
            if (!signatures.add(signature)) continue;
            groups.add(new SpecGroup(title, items));
            total += items.size();
        }
        return groups;
    }

    /**
     * Generic grouped fallback for definition lists: every {@code <dl>} with
     * spec-like dt/dd rows becomes a group, titled by the nearest preceding
     * heading. Consecutive lists resolving to the same title are merged. A single
     * stray row is not enough — at least 2 rows in total are required, matching
     * the table extractor's noise bar.
     */
    private List<SpecGroup> extractDlSpecGroups(Document doc) {
        List<SpecGroup> groups = new ArrayList<>();
        int total = 0;
        for (Element dl : doc.select("dl")) {
            if (groups.size() >= MAX_SPEC_GROUPS || total >= MAX_TOTAL_SPEC_ITEMS) break;
            var dts = dl.select("dt");
            var dds = dl.select("dd");
            int n = Math.min(dts.size(), dds.size());
            List<SpecItem> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < n && items.size() < MAX_ITEMS_PER_GROUP; i++) {
                String k = cleanSpecLabel(dts.get(i).text());
                String v = dds.get(i).text().trim();
                if (looksLikeSpec(k, v) && seen.add(k.toLowerCase(Locale.ROOT))) {
                    items.add(new SpecItem(k, v));
                }
            }
            if (items.isEmpty()) continue;
            String title = nearestHeadingTitle(dl);
            SpecGroup last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null && last.title().equals(title)) {
                List<SpecItem> merged = new ArrayList<>(last.items());
                merged.addAll(items);
                groups.set(groups.size() - 1, new SpecGroup(title, merged));
            } else {
                groups.add(new SpecGroup(title, items));
            }
            total += items.size();
        }
        return total >= 2 ? groups : List.of();
    }

    /**
     * Grouped fallback for label-emphasized list items: {@code <li><em>Label</em>Value</li>}
     * (Miele product descriptions use exactly this, with a {@code <p><strong>Title</strong></p>}
     * paragraph before each list). Items without a value (label-only bullets) are skipped.
     */
    private List<SpecGroup> extractLabeledListSpecGroups(Document doc) {
        List<SpecGroup> groups = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        int total = 0;
        for (Element list : doc.select("ol, ul")) {
            if (groups.size() >= MAX_SPEC_GROUPS || total >= MAX_TOTAL_SPEC_ITEMS) break;
            List<SpecItem> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Element li : list.children()) {
                if (!li.is("li") || items.size() >= MAX_ITEMS_PER_GROUP) continue;
                Element lab = li.children().isEmpty() ? null : li.child(0);
                if (lab == null || !lab.is("em, strong, b")) continue;
                String label = cleanSpecLabel(lab.text());
                String full = li.text().trim();
                String value = full.length() > label.length() && full.startsWith(label)
                        ? full.substring(label.length()).trim()
                        : li.ownText().trim();
                if (value.isEmpty()) continue; // label-only bullet
                if (looksLikeSpec(label, value) && seen.add(label.toLowerCase(Locale.ROOT))) {
                    items.add(new SpecItem(label, value));
                }
            }
            if (items.isEmpty()) continue;
            String title = boldedParagraphTitle(list);
            String signature = title + "|" + items;
            if (!signatures.add(signature)) continue;
            SpecGroup last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null && last.title().equals(title)) {
                List<SpecItem> merged = new ArrayList<>(last.items());
                merged.addAll(items);
                groups.set(groups.size() - 1, new SpecGroup(title, merged));
            } else {
                groups.add(new SpecGroup(title, items));
            }
            total += items.size();
        }
        return total >= 2 ? groups : List.of();
    }

    /**
     * Title for a labeled list: an immediately preceding paragraph that is just a
     * bolded phrase (Miele: {@code <p><strong>Teknik veriler</strong>&nbsp;</p>}),
     * else the generic nearest-heading lookup.
     */
    private static String boldedParagraphTitle(Element list) {
        Element prev = list.previousElementSibling();
        if (prev != null && prev.is("p, div")) {
            Element bold = prev.selectFirst("strong, b");
            if (bold != null) {
                String boldText = bold.text().trim();
                String prevText = prev.text().trim();
                // The paragraph must be essentially just the bold phrase (a title),
                // not body copy that merely contains bold words.
                if (!boldText.isEmpty() && boldText.length() <= 60
                        && prevText.length() <= boldText.length() + 5) {
                    return boldText;
                }
            }
        }
        return nearestHeadingTitle(list);
    }

    /** Group title for a spec table: caption → nearest preceding heading → default. */
    private static String tableGroupTitle(Element table) {
        Element caption = table.selectFirst("caption");
        if (caption != null) {
            String t = caption.text().trim();
            if (!t.isEmpty() && t.length() <= 60) return t;
        }
        return nearestHeadingTitle(table);
    }

    /**
     * Nearest preceding heading for a spec container: walks previous siblings,
     * climbing up to 3 ancestor levels. Besides h2-h5, class-based titles are
     * accepted (e.g. Philips "p-s08__spec-title" paragraphs).
     */
    private static String nearestHeadingTitle(Element el) {
        final String SEL = "h2, h3, h4, h5, [class*=spec-title], [class*=section-title], [class*=group-title]";
        Element scope = el;
        for (int depth = 0; depth < 3 && scope != null; depth++) {
            for (Element prev = scope.previousElementSibling(); prev != null; prev = prev.previousElementSibling()) {
                Element h = prev.is(SEL) ? prev : prev.selectFirst(SEL);
                if (h != null) {
                    String t = h.text().trim();
                    if (!t.isEmpty() && t.length() <= 60) return t;
                }
            }
            scope = scope.parent();
        }
        return "Teknik Özellikler";
    }

    /**
     * Unescapes a JS string literal body ({@code \" \\ \/ \n \r \t \b \f \ uXXXX}).
     * Unknown escapes keep the escaped character as-is.
     */
    static String unescapeJsString(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append(n);
                        }
                    } else {
                        out.append(n);
                    }
                }
                default -> out.append(n); // \" \\ \/ and anything else → the char itself
            }
        }
        return out.toString();
    }

    /**
     * Extracts a balanced JSON array starting at {@code start} ('['), respecting
     * string literals and their escapes. Returns null when unbalanced/oversized.
     */
    static String extractBalancedArray(String text, int start) {
        if (start < 0 || start >= text.length() || text.charAt(start) != '[') return null;
        final int cap = Math.min(text.length(), start + 800_000); // sanity bound
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < cap; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') i++;          // skip escaped char inside string
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    /** Brand name extraction (schema.org brand or meta). */
    String extractBrand(Document doc) {
        // 1) JSON-LD brand.name
        try {
            for (var el : doc.select("script[type=application/ld+json]")) {
                String json = el.data();
                if (json == null) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\"brand\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(json);
                if (m.find()) return m.group(1).trim();
            }
        } catch (Exception ignored) {}

        // 2) Itemprop brand
        var b = doc.selectFirst("[itemprop=brand]");
        if (b != null) {
            String txt = b.text();
            if (txt != null && !txt.isBlank() && txt.length() < 50) return txt.trim();
        }

        // 3) Meta product:brand (OG product extension)
        String og = doc.select("meta[property=product:brand]").attr("content");
        if (og != null && !og.isBlank() && og.length() < 50) return og.trim();

        return null;
    }

    /** HTML cleanup — remove script/style, collapse excess whitespace. */
    private String cleanHtml(String html) {
        if (html == null) return null;
        // Remove dangerous tags (script, style, on* handlers)
        String cleaned = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?i)\\son[a-z]+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\son[a-z]+\\s*=\\s*'[^']*'", "");
        // Allowed tags: p, br, strong, b, em, i, ul, ol, li, h2-h6, span, div
        // Leave other tags as text (Jsoup safelist is stronger, but this is simpler)
        return cleaned.trim();
    }

    /** Sentence-aware truncation for the description (cut at the last sentence end within max). */
    private String truncateSmart(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        int idx = s.lastIndexOf('.', max);
        if (idx > max - 200) return s.substring(0, idx + 1);
        return s.substring(0, max) + "...";
    }

    private List<String> extractImageUrls(Document doc, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();

        // 1) og:image — usually the main product image, most reliable
        for (Element e : doc.select("meta[property=og:image], meta[property=og:image:secure_url], meta[name=og:image]")) {
            addAbsolute(urls, e.attr("content"), baseUrl);
        }
        // twitter:image
        for (Element e : doc.select("meta[name=twitter:image], meta[property=twitter:image]")) {
            addAbsolute(urls, e.attr("content"), baseUrl);
        }

        // 2) JSON-LD structured data (Schema.org Product.image)
        for (Element script : doc.select("script[type=application/ld+json]")) {
            extractFromJsonLd(script.data(), urls, baseUrl);
        }

        // 3) DOM <img> tags (lazy-load + srcset supported)
        for (Element img : doc.select("img")) {
            if (isLikelyJunk(img)) continue;
            // Try high-resolution sources first
            String src = firstNonBlank(
                    img.attr("data-zoom-image"),       // zoomed variant
                    img.attr("data-large-src"),
                    img.attr("data-original"),
                    img.attr("data-src"),
                    img.attr("src"));
            if (img.hasAttr("srcset")) {
                String largest = pickLargestFromSrcset(img.attr("srcset"));
                if (largest != null) src = largest;
            }
            addAbsolute(urls, src, baseUrl);
        }

        // 4) <link rel="preload" as="image">
        for (Element link : doc.select("link[rel=preload][as=image], link[rel=image_src]")) {
            addAbsolute(urls, link.attr("href"), baseUrl);
        }

        // Filters
        return urls.stream()
                .filter(this::isImageUrl)
                .filter(u -> !isProbablyJunkUrl(u))
                .distinct()
                .limit(MAX_IMAGES)
                .toList();
    }

    /** Extracts the Product.image field from JSON-LD (simple pattern; an alternative to full LD-JSON parsing). */
    private void extractFromJsonLd(String json, Set<String> urls, String baseUrl) {
        if (json == null || json.isBlank()) return;
        // "image": "https://..."  or  "image": ["https://...", ...]
        Pattern singleStr = Pattern.compile("\"image\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m1 = singleStr.matcher(json);
        while (m1.find()) addAbsolute(urls, m1.group(1), baseUrl);

        Pattern arr = Pattern.compile("\"image\"\\s*:\\s*\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m2 = arr.matcher(json);
        while (m2.find()) {
            String inner = m2.group(1);
            Matcher m3 = Pattern.compile("\"([^\"]+)\"").matcher(inner);
            while (m3.find()) addAbsolute(urls, m3.group(1), baseUrl);
        }
    }

    /** srcset="url1 1x, url2 2x" or "url1 320w, url2 640w" → return the one with the highest descriptor. */
    static String pickLargestFromSrcset(String srcset) {
        if (srcset == null || srcset.isBlank()) return null;
        String[] parts = srcset.split(",");
        String bestUrl = null;
        double bestScore = -1;
        for (String part : parts) {
            String p = part.trim();
            String[] tok = p.split("\\s+");
            if (tok.length == 0) continue;
            String url = tok[0];
            double score = 1;
            if (tok.length >= 2) {
                String desc = tok[1];
                try {
                    if (desc.endsWith("w")) score = Double.parseDouble(desc.substring(0, desc.length() - 1));
                    else if (desc.endsWith("x")) score = Double.parseDouble(desc.substring(0, desc.length() - 1)) * 1000;
                } catch (NumberFormatException ignored) {}
            }
            if (score > bestScore) {
                bestScore = score;
                bestUrl = url;
            }
        }
        return bestUrl;
    }

    /** Filters out clearly identifiable junk such as "logo", "icon", "sprite", and ad images. */
    private boolean isLikelyJunk(Element img) {
        String alt = img.attr("alt").toLowerCase();
        String cls = img.attr("class").toLowerCase();
        String src = (img.attr("src") + " " + img.attr("data-src")).toLowerCase();
        String[] junk = { "logo", "icon", "sprite", "favicon", "social", "facebook",
                          "twitter", "instagram", "youtube", "whatsapp", "loader",
                          "spinner", "placeholder" };
        for (String j : junk) {
            if (alt.contains(j) || cls.contains(j) || src.contains(j)) return true;
        }
        // 1x1 pixel tracking?
        try {
            String w = img.attr("width"), h = img.attr("height");
            if (!w.isBlank() && Integer.parseInt(w) <= 30) return true;
            if (!h.isBlank() && Integer.parseInt(h) <= 30) return true;
        } catch (NumberFormatException ignored) {}
        return false;
    }

    private boolean isProbablyJunkUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("/logo")
                || lower.contains("/sprite")
                || lower.contains("/icon")
                || lower.contains("favicon")
                || lower.contains("/banner/")
                || lower.contains("placeholder")
                || lower.contains("blank.gif");
    }

    private boolean isImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        // Strip query string
        int q = lower.indexOf('?');
        String path = q >= 0 ? lower.substring(0, q) : lower;
        // Vector/app assets are not raster product photos — never treat as candidates.
        if (path.endsWith(".svg") || path.endsWith(".ico")) return false;
        // Next.js app bundles its own static assets (logos/icons) under /_next/static/;
        // those slip through the "/media/" heuristic, so exclude them explicitly.
        if (lower.contains("/_next/")) return false;
        return path.endsWith(".jpg") || path.endsWith(".jpeg")
                || path.endsWith(".png") || path.endsWith(".webp")
                || path.endsWith(".avif") || path.endsWith(".gif")
                // Some CDNs have no extension; checked via content-type
                || lower.contains("/image/") || lower.contains("/images/")
                || lower.contains("/media/") || lower.contains("/cdn-cgi/image/");
    }

    private void addAbsolute(Set<String> urls, String src, String baseUrl) {
        if (src == null) return;
        src = src.trim();
        if (src.isEmpty() || src.startsWith("data:")) return;
        try {
            URI uri = URI.create(src);
            URI absolute = uri.isAbsolute() ? uri : URI.create(baseUrl).resolve(uri);
            String s = absolute.toString();
            // Accept at least URLs with an image extension or 'image' in the path
            urls.add(s);
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────
    //  Image download
    // ─────────────────────────────────────────────────────────────

    private ImageDownload downloadImage(String url) {
        return downloadImage(url, null);
    }

    /**
     * Public proxy: fetches images from hotlink-protected CDNs through the backend
     * with a Referer header, so they can be shown in admin UI thumbnails.
     * SSRF guards still apply (no private IPs; an allowlist isn't required because the images may live on a CDN).
     */
    public ImageDownload proxyImage(String url, String referer) {
        if (url == null || url.isBlank()) return null;
        return downloadImage(url, referer);
    }

    /**
     * Referer-aware variant — some CDNs (WitCDN/Fakir, etc.) are hotlink-protected:
     * they respond with a placeholder to requests that lack a Referer header or
     * come from the wrong origin. Sending the page URL as the Referer fixes this.
     */
    private ImageDownload downloadImage(String url, String referer) {
        // The image host may or may not be in the allowlist, or it may be a CDN — be flexible.
        // Still do a scheme + IP check for SSRF.
        try {
            // The image host does not have to be on the allowlist (CDNs vary), but the
            // destination must still be a public address — on the first request AND on
            // every redirect. setInstanceFollowRedirects(true) used to hand a 302 the
            // right to point anywhere, including the container's own network.
            URI current = SsrfGuard.validate(url);
            HttpURLConnection conn = null;
            int code = 0;
            for (int hop = 0; hop <= SsrfGuard.MAX_REDIRECTS; hop++) {
                conn = (HttpURLConnection) current.toURL().openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*,*/*;q=0.8");
                if (referer != null && !referer.isBlank()) {
                    conn.setRequestProperty("Referer", referer);
                }
                conn.setConnectTimeout(IMAGE_DOWNLOAD_TIMEOUT_MS);
                conn.setReadTimeout(IMAGE_DOWNLOAD_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);

                code = conn.getResponseCode();
                if (code < 300 || code >= 400) break;
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank() || hop == SsrfGuard.MAX_REDIRECTS) return null;
                current = SsrfGuard.validateRedirect(current, location);
            }
            if (code < 200 || code >= 300) return null;

            String contentType = conn.getContentType();
            long contentLength = conn.getContentLengthLong();
            if (contentLength > MAX_IMAGE_BYTES) return null;

            try (InputStream in = conn.getInputStream();
                 var baos = new java.io.ByteArrayOutputStream(Math.max(1024, (int) Math.min(Integer.MAX_VALUE, contentLength)))) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buf)) > 0) {
                    total += read;
                    if (total > MAX_IMAGE_BYTES) return null; // streaming size limit
                    baos.write(buf, 0, read);
                }
                return new ImageDownload(baos.toByteArray(), contentType);
            }
        } catch (Exception e) {
            log.debug("[Crawler] image download fail {}: {}", shortenUrl(url), e.getMessage());
            return null;
        }
    }

    /** Checks via magic bytes whether it is actually an image (the extension may lie). */
    private boolean isValidImageMagic(byte[] b) {
        if (b == null || b.length < 8) return false;
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return true;
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;
        // GIF: GIF87a / GIF89a
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return true;
        // WebP: RIFF....WEBP
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return true;
        // AVIF: ftyp...avif
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return true;
        return false;
    }

    private String inferFilename(String url, String contentType) {
        try {
            String path = URI.create(url).getPath();
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (name.contains("?")) name = name.substring(0, name.indexOf('?'));
            if (name.isBlank()) name = "image";
            // If there is no extension, add one based on content-type
            if (!name.contains(".")) {
                String ext = ".jpg";
                if (contentType != null) {
                    String ct = contentType.toLowerCase();
                    if (ct.contains("png")) ext = ".png";
                    else if (ct.contains("webp")) ext = ".webp";
                    else if (ct.contains("avif")) ext = ".avif";
                    else if (ct.contains("gif")) ext = ".gif";
                }
                name += ext;
            }
            return sanitizeFilename(name);
        } catch (Exception e) {
            return "image.jpg";
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ─────────────────────────────────────────────────────────────
    //  Validation / SSRF guard
    // ─────────────────────────────────────────────────────────────

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new CrawlException("URL boş olamaz");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new CrawlException("Geçersiz URL formatı");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new CrawlException("Sadece http(s) URL'leri kabul edilir");
        }
        String rawHost = uri.getHost();
        if (rawHost == null) throw new CrawlException("Host bulunamadı");
        final String host = rawHost.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!allowed) {
            throw new CrawlException("Bu domain destek dışında. Desteklenen: " + String.join(", ", ALLOWED_HOSTS));
        }
        // The allowlist is a business rule ("which suppliers do we support"); the
        // address check below is the SSRF control, and both have to hold.
        assertNotPrivateIp(host);
    }

    /**
     * Delegates to {@link SsrfGuard}, which checks every address the host resolves to
     * (not just the first), understands IPv6 unique-local and IPv4-mapped addresses, and
     * covers the whole 100.64.0.0/10 CGNAT block rather than a single /24 of it.
     */
    private void assertNotPrivateIp(String host) {
        try {
            SsrfGuard.assertPublicAddress(host);
        } catch (SsrfGuard.BlockedTargetException e) {
            throw new CrawlException(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private static String shortenUrl(String url) {
        if (url == null) return "";
        return url.length() <= 80 ? url : url.substring(0, 77) + "...";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ─────────────────────────────────────────────────────────────
    //  DTOs / Records
    // ─────────────────────────────────────────────────────────────

    /**
     * Crawler preview result — information to display to the admin.
     *
     * @param url           source URL
     * @param title         page title (og:title or &lt;title&gt;)
     * @param images        candidate image URLs
     * @param description   long description (may be HTML; plain text preferred)
     * @param shortDescription short description (1-2 sentences, from the meta description)
     * @param specs         technical specifications, flattened (key→value pairs)
     * @param specGroups    technical specifications preserving the page's section
     *                      grouping ("Genel özellikler", "Boyutlar", …) — matches the
     *                      product's structured technicalSpecs shape
     * @param brand         brand name (from schema.org/brand)
     * @param error         error message (if any)
     */
    public record CrawlPreview(
            String url,
            String title,
            List<String> images,
            String description,
            String shortDescription,
            java.util.Map<String, String> specs,
            List<SpecGroup> specGroups,
            String brand,
            String error) {

        /** Legacy 4-arg constructor — for backward compatibility. */
        public CrawlPreview(String url, String title, List<String> images, String error) {
            this(url, title, images, null, null, java.util.Map.of(), List.of(), null, error);
        }
    }

    /** One spec section as it appears on the source page (e.g. "Soğutucu bölümü"). */
    public record SpecGroup(String title, List<SpecItem> items) {}

    /** One label/value row inside a spec section. */
    public record SpecItem(String label, String value) {}
    public record ImportResult(int success, int total, List<String> errors) {
        public boolean isOk() { return success > 0 && errors.isEmpty(); }
    }
    public static class CrawlException extends RuntimeException {
        public CrawlException(String message) { super(message); }
        public CrawlException(String message, Throwable cause) { super(message, cause); }
    }
    public record ImageDownload(byte[] bytes, String contentType) {}
}
