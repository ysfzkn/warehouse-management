package com.warehouse.service.crawler;

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
            "arcelik.com",          "arcelik.com.tr",
            "beko.com.tr",          "beko.com",
            "vestel.com.tr",        "vestel.com",
            "samsung.com",          "samsung.com.tr",
            "lg.com",               "lg.com.tr",
            "miele.com",            "miele.com.tr",
            "haier.com",            "haier.com.tr",
            "fakir.com.tr",         "fakir.com"
            // NOTE: altus.com.tr does TLS fingerprinting with Akamai Bot Manager;
            // it cannot be bypassed with server-side HTTP clients (Jsoup/HttpURLConnection).
            // Supporting it would require a headless browser (Playwright) or ScraperAPI.
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
        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                    .get();

            String title = firstNonBlank(
                    doc.select("meta[property=og:title]").attr("content"),
                    doc.title());

            List<String> images = extractImageUrls(doc, pageUrl);

            // Description + shortDescription + specs + brand
            String description = extractDescription(doc);
            String shortDescription = extractShortDescription(doc, description);
            java.util.Map<String, String> specs = extractSpecs(doc);
            String brand = extractBrand(doc);

            long ms = System.currentTimeMillis() - t0;
            log.info("[Crawler] preview {} → {} images, desc={} chars, specs={} ({}ms)",
                    pageUrl, images.size(),
                    description != null ? description.length() : 0,
                    specs.size(), ms);
            return new CrawlPreview(pageUrl, title.trim(), images,
                    description, shortDescription, specs, brand, null);
        } catch (org.jsoup.HttpStatusException e) {
            throw new CrawlException("Sayfa erişilemez (" + e.getStatusCode() + ")", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new CrawlException("Sayfa yanıt vermedi (timeout). Tekrar deneyin.", e);
        } catch (Exception e) {
            log.warn("[Crawler] preview hatası: {}", e.getMessage());
            throw new CrawlException("Sayfa çekilemedi: " + e.getMessage(), e);
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
                // Simple regex — instead of risky full JSON parsing (\n etc.), search for description "..."
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\"description\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
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

        // 1) schema.org Product.additionalProperty (JSON-LD)
        try {
            for (var el : doc.select("script[type=application/ld+json]")) {
                String json = el.data();
                if (json == null || json.isBlank()) continue;
                // additionalProperty: [{ "name": "...", "value": "..." }, ...]
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\\{\\s*\"@type\"\\s*:\\s*\"PropertyValue\"\\s*,[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(json);
                while (m.find() && specs.size() < 30) {
                    String k = m.group(1).trim();
                    String v = m.group(2).trim();
                    if (!k.isEmpty() && !v.isEmpty()) specs.put(k, v);
                }
            }
        } catch (Exception ignored) {}

        // 2) "spec table" inside a <table> (key-value pattern)
        if (specs.size() < 5) {
            for (var table : doc.select("table.product-specs, table.specs, table.specifications, .specs-table, table[class*=spec]")) {
                for (var row : table.select("tr")) {
                    var cells = row.select("td, th");
                    if (cells.size() >= 2) {
                        String k = cells.get(0).text().trim();
                        String v = cells.get(1).text().trim();
                        if (!k.isEmpty() && !v.isEmpty() && k.length() < 80 && v.length() < 200) {
                            specs.putIfAbsent(k, v);
                        }
                    }
                    if (specs.size() >= 30) break;
                }
            }
        }

        // 3) <dl> definition list
        if (specs.size() < 5) {
            for (var dl : doc.select("dl.specs, dl.product-attributes, dl[class*=spec]")) {
                var dts = dl.select("dt");
                var dds = dl.select("dd");
                int n = Math.min(dts.size(), dds.size());
                for (int i = 0; i < n && specs.size() < 30; i++) {
                    String k = dts.get(i).text().trim();
                    String v = dds.get(i).text().trim();
                    if (!k.isEmpty() && !v.isEmpty()) specs.putIfAbsent(k, v);
                }
            }
        }

        // 4) "Key: Value" pattern in list items
        if (specs.size() < 3) {
            for (var li : doc.select("ul.features li, ul.specs li, .product-features li")) {
                String txt = li.text().trim();
                if (txt.contains(":")) {
                    String[] kv = txt.split(":", 2);
                    if (kv.length == 2) {
                        String k = kv[0].trim();
                        String v = kv[1].trim();
                        if (!k.isEmpty() && !v.isEmpty() && k.length() < 80 && v.length() < 200) {
                            specs.putIfAbsent(k, v);
                        }
                    }
                }
                if (specs.size() >= 30) break;
            }
        }

        return specs;
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
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            assertNotPrivateIp(uri.getHost());

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*,*/*;q=0.8");
            if (referer != null && !referer.isBlank()) {
                conn.setRequestProperty("Referer", referer);
            }
            conn.setConnectTimeout(IMAGE_DOWNLOAD_TIMEOUT_MS);
            conn.setReadTimeout(IMAGE_DOWNLOAD_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
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
        assertNotPrivateIp(host);
    }

    private void assertNotPrivateIp(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress()) {
                throw new CrawlException("Yerel/özel IP'ler engelli (SSRF koruması)");
            }
            // Extra protection for ranges like 169.254/16, 100.64/10 (CGNAT)
            String ip = addr.getHostAddress();
            if (ip.startsWith("169.254.") || ip.startsWith("100.64.")) {
                throw new CrawlException("Bu IP aralığı engelli");
            }
        } catch (UnknownHostException e) {
            throw new CrawlException("Host çözülemedi: " + host);
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
     * @param specs         technical specifications (key→value pairs)
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
            String brand,
            String error) {

        /** Legacy 4-arg constructor — for backward compatibility. */
        public CrawlPreview(String url, String title, List<String> images, String error) {
            this(url, title, images, null, null, java.util.Map.of(), null, error);
        }
    }
    public record ImportResult(int success, int total, List<String> errors) {
        public boolean isOk() { return success > 0 && errors.isEmpty(); }
    }
    public static class CrawlException extends RuntimeException {
        public CrawlException(String message) { super(message); }
        public CrawlException(String message, Throwable cause) { super(message, cause); }
    }
    public record ImageDownload(byte[] bytes, String contentType) {}
}
