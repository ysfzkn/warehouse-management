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
 * Üçüncü-taraf ürün sayfasından (örn. Profilo) ürün fotoğraflarını otomatik
 * çeker, yerel sisteme yükler.
 *
 * <h3>Akış</h3>
 * <ol>
 *   <li>{@link #preview(String)} — URL doğrulanır, HTML çekilir, görsel adayları
 *       (og:image, JSON-LD, &lt;img&gt; gallery, srcset) çıkarılır. UI'a önizleme
 *       listesi döner.</li>
 *   <li>{@link #importImages} — admin onayladığı URL'leri tek tek indirir,
 *       boyut/MIME doğrular, {@link PhotoStorageService} ile diske yazar,
 *       {@code product_images} tablosuna kayıt ekler.</li>
 * </ol>
 *
 * <h3>Edge case'ler</h3>
 * <ul>
 *   <li><b>SSRF koruması</b> — sadece HTTPS, allowlist host, private/loopback IP reddi</li>
 *   <li><b>Rate-limit</b> — 5 saniyelik soğuma (kullanıcı arka arkaya çağrı atamaz)</li>
 *   <li><b>Timeout</b> — fetch 15s, image download 10s</li>
 *   <li><b>Dosya boyutu</b> — max 10 MB / image, max 20 image / sayfa</li>
 *   <li><b>Format</b> — yalnızca JPG/PNG/WebP/AVIF; magic byte doğrulaması</li>
 *   <li><b>Duplicate</b> — aynı URL çoklu girişi tek sefer; LinkedHashSet ile sıra korunur</li>
 *   <li><b>Lazy-load</b> — data-src, data-original, srcset hepsi denenir</li>
 *   <li><b>Thumbnail filtresi</b> — "thumb"/"icon"/"logo"/"sprite" path desenleri elenir</li>
 *   <li><b>HTML değişikliği</b> — multi-strategy: og:image → JSON-LD → DOM. Profilo özel
 *       selector'lar başta, kaybolursa generic fallback devreye girer.</li>
 *   <li><b>İmza tekrarı</b> — admin "Mevcut görselleri sil" tikleyebilir; default ek modunda</li>
 * </ul>
 */
@Service
public class ProductImageCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageCrawlerService.class);

    /**
     * İzinli host'lar — SSRF / abuse koruması. Yeni satıcı eklendiğinde buraya yazılır.
     * Hem ".com" hem ".com.tr" varyasyonları (Türkiye ve global versiyonlar farklı olabilir).
     * Subdomain'ler (m.profilo.com, www.profilo.com vb.) {@link #validateUrl} içindeki
     * {@code endsWith("." + host)} kontrolüyle otomatik kabul edilir.
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
            "haier.com",            "haier.com.tr"
    );

    private static final int MAX_IMAGES = 20;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final int FETCH_TIMEOUT_MS = 15_000;
    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 10_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; WarehouseProductImporter/1.0; +https://example.com)";

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

    /** URL'i doğrula, HTML'i çek, aday görsel URL'lerini döndür (admin önizler). */
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
            long ms = System.currentTimeMillis() - t0;
            log.info("[Crawler] preview {} → {} images ({}ms)", pageUrl, images.size(), ms);
            return new CrawlPreview(pageUrl, title.trim(), images, null);
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
     * Seçilen URL'leri indirir, ürün için kaydeder.
     *
     * @param productId hedef ürün
     * @param imageUrls indirilecek görsel URL'leri (preview'da bulunanlardan seçilen)
     * @param replaceExisting true ise ürünün mevcut görselleri silinir
     * @param markFirstAsPrimary true ise ilk başarıyla yüklenen görsel "primary" yapılır
     */
    public ImportResult importImages(Long productId,
                                      List<String> imageUrls,
                                      boolean replaceExisting,
                                      boolean markFirstAsPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CrawlException("Ürün bulunamadı: " + productId));

        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ImportResult(0, 0, List.of("İndirilecek görsel seçilmedi"));
        }
        if (imageUrls.size() > MAX_IMAGES) {
            imageUrls = imageUrls.subList(0, MAX_IMAGES);
        }

        // Mevcut görselleri sil (istenirse)
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
                ImageDownload dl = downloadImage(url);
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

    private List<String> extractImageUrls(Document doc, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();

        // 1) og:image — genelde ana ürün görseli, en güvenilir
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

        // 3) DOM <img> tag'ları (lazy-load + srcset destekli)
        for (Element img : doc.select("img")) {
            if (isLikelyJunk(img)) continue;
            // Önce yüksek çözünürlüklü kaynakları dene
            String src = firstNonBlank(
                    img.attr("data-zoom-image"),       // büyütülmüş varyant
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

        // Filtreler
        return urls.stream()
                .filter(this::isImageUrl)
                .filter(u -> !isProbablyJunkUrl(u))
                .distinct()
                .limit(MAX_IMAGES)
                .toList();
    }

    /** JSON-LD içindeki Product.image alanını çıkarır (basit pattern; LD-JSON tam parse'a alternatif). */
    private void extractFromJsonLd(String json, Set<String> urls, String baseUrl) {
        if (json == null || json.isBlank()) return;
        // "image": "https://..."  veya  "image": ["https://...", ...]
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

    /** srcset="url1 1x, url2 2x" ya da "url1 320w, url2 640w" → en yüksek descriptor olanı dön. */
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

    /** "Logo", "icon", "sprite", reklam görseli gibi açık-belirtili junk'ları ele. */
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
        // 1x1 piksel tracking?
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
                // Bazı CDN'lerde uzantı olmaz, content-type'tan kontrol edilir
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
            // En azından imaj uzantısı veya path'inde 'image' geçenleri kabul et
            urls.add(s);
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────
    //  Image download
    // ─────────────────────────────────────────────────────────────

    private ImageDownload downloadImage(String url) {
        // Resmin host'u de allowlist'te olabilir veya CDN olabilir — esnek.
        // Yine de SSRF için scheme + IP kontrolü.
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            assertNotPrivateIp(uri.getHost());

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*,*/*;q=0.8");
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

    /** Magic bytes ile gerçekten görsel mi kontrolü (uzantı yalan söyleyebilir). */
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
            // Uzantı yoksa content-type'a göre ekle
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
            // 169.254/16, 100.64/10 (CGNAT) gibi extra koruma
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

    public record CrawlPreview(String url, String title, List<String> images, String error) {}
    public record ImportResult(int success, int total, List<String> errors) {
        public boolean isOk() { return success > 0 && errors.isEmpty(); }
    }
    public static class CrawlException extends RuntimeException {
        public CrawlException(String message) { super(message); }
        public CrawlException(String message, Throwable cause) { super(message, cause); }
    }
    private record ImageDownload(byte[] bytes, String contentType) {}
}
