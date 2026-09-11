package com.warehouse.service.crawler;

import com.warehouse.entity.Product;
import com.warehouse.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Finds the supplier page for a product we hold no photos for.
 *
 * <h3>How a link is discovered</h3>
 * Site search would mean writing and maintaining a bespoke scraper for every supplier's
 * result page. Sitemaps give the same answer generically: nearly every one of these
 * vendors publishes {@code /sitemap.xml}, the product URLs it lists carry the model code
 * in the slug, and one fetch covers the whole catalogue. Matching is then a string
 * question — does this product's stock code appear in that URL — rather than an HTML
 * parsing question that breaks on the next redesign.
 *
 * <p>Verified against the live sitemaps: Simfer, Ferre, Hoover, Kumtel, Fakir, Kärcher,
 * Rota and Profilo all serve one. Philips does not, and is simply skipped.
 *
 * <h3>Why the brand decides the host</h3>
 * A product only ever appears on its own manufacturer's site, so the brand narrows the
 * search to a single sitemap instead of all of them.
 */
@Service
public class SupplierLinkFinder {

    private static final Logger log = LoggerFactory.getLogger(SupplierLinkFinder.class);

    private static final Pattern LOC = Pattern.compile("<loc>\\s*(.*?)\\s*</loc>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int FETCH_TIMEOUT_MS = 20_000;
    private static final long MAX_SITEMAP_BYTES = 12L * 1024 * 1024;
    /** Child sitemaps to follow out of an index, product ones first. */
    private static final int MAX_CHILD_SITEMAPS = 12;
    private static final int MAX_URLS_PER_HOST = 40_000;
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L;

    /**
     * A code shorter than this is not evidence: "AL" or "210" occurs in half the slugs
     * on a catalogue site.
     */
    private static final int MIN_TOKEN_LENGTH = 4;

    private final Map<String, CachedUrls> cache = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Best supplier URL for this product, or null when the brand maps to no supported
     * site, the site publishes no usable sitemap, or nothing in it carries the code.
     */
    public String find(Product product) {
        if (product == null) return null;
        String host = hostForBrand(brandName(product));
        if (host == null) return null;

        List<String> tokens = codeTokens(product);
        if (tokens.isEmpty()) return null;

        // A site that addresses products by model code answers exactly, so it is tried
        // first and its answer is verified rather than guessed at.
        String direct = findByUrlPattern(host, tokens);
        if (direct != null) return direct;

        List<String> urls = urlsFor(host);
        if (urls.isEmpty()) return null;

        String best = null;
        int bestToken = 0;
        for (String url : urls) {
            String slug = normalise(url);
            for (String token : tokens) {
                // A longer matching code is a more specific hit: prefer "SR2515" over
                // whatever shorter fragment also happened to appear.
                if (token.length() > bestToken && slug.contains(token)) {
                    best = url;
                    bestToken = token.length();
                }
            }
        }
        if (best != null) {
            log.debug("[LinkFinder] {} -> {}", product.getSku(), best);
        }
        return best;
    }

    /**
     * Sites whose product URL is the model code, so the address can be built instead of
     * searched for. Profilo (the BSH platform) answers
     * {@code /tr/tr/product/FRGA103B} with exactly that oven — verified live against
     * FRGA103B, FRIAT8AB, BM4381EG, 42PA300E and FXSA344C.
     *
     * <p>This matters because a sitemap only lists what the vendor chose to publish:
     * Simfer's carries 274 products and none of the ones missing photos here, and
     * Profilo's is mostly spare parts. A constructed URL has no such gap.
     */
    private static final Map<String, String> URL_PATTERNS = Map.of(
            "profilo.com", "https://www.profilo.com/tr/tr/product/%s");

    /**
     * Builds the address from the model code and keeps it only if the page that comes
     * back names that same code.
     *
     * <p>The confirmation is what makes this safe: a wrong guess lands on a 404 or on
     * some other product, and either way the title will not contain the code, so the
     * candidate is dropped instead of being offered.
     */
    private String findByUrlPattern(String host, List<String> tokens) {
        String pattern = URL_PATTERNS.get(host);
        if (pattern == null) return null;

        for (String token : tokens) {
            String url = String.format(pattern, token);
            String body = fetch(url);
            if (body == null) continue;
            String title = titleOf(body);
            if (title != null && normalise(title).contains(token)) {
                log.debug("[LinkFinder] pattern hit {} -> {}", token, url);
                return url;
            }
        }
        return null;
    }

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static String titleOf(String html) {
        Matcher m = TITLE.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Which supported site, if any, hosts this brand's catalogue. */
    String hostForBrand(String brand) {
        String b = normalise(brand);
        if (b.length() < 3) return null;
        for (String host : ProductImageCrawlerService.allowedHosts()) {
            // Compare against the registrable name only: "ferreturkiye.com" -> FERRETURKIYE,
            // which still starts with the brand FERRE.
            String name = normalise(host.split("\\.")[0]);
            if (name.startsWith(b) || b.startsWith(name)) return host;
        }
        return null;
    }

    /**
     * The distinctive parts of a stock code, longest first.
     *
     * <p>Codes are written as "Simfer 40 SFSW4M" or "Marsstar MS-210" — a brand followed
     * by the model. The brand is dropped, because every URL on that supplier's site
     * contains it and it would match all of them.
     */
    List<String> codeTokens(Product product) {
        String sku = product.getSku();
        if (sku == null || sku.isBlank()) return List.of();
        String brand = normalise(brandName(product));

        Set<String> out = new LinkedHashSet<>();
        String whole = normalise(sku);
        // "Simfer 40 SFSW4M" -> "40SFSW4M": the code without its brand prefix is the
        // strongest single token, and slugs write it with the separators stripped.
        if (!brand.isEmpty() && whole.startsWith(brand) && whole.length() > brand.length()) {
            addToken(out, whole.substring(brand.length()));
        }
        addToken(out, whole);
        for (String part : sku.split("[\\s\\-_/,.]+")) {
            String t = normalise(part);
            if (!t.equals(brand)) addToken(out, t);
        }
        List<String> tokens = new ArrayList<>(out);
        tokens.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return tokens;
    }

    /**
     * Keeps a token only if it could be a model code.
     *
     * <p>The digit requirement is the important half. Stock codes here are written as
     * "Ferre 35 Beyaz" — brand, number, colour — and the colour survived every length
     * rule, so "BEYAZ" was offered as evidence and matched the first white appliance in
     * the catalogue: a live run proposed the MF-42 oven's photos for the 35 L one. A
     * model code effectively always carries a digit; a Turkish colour or category word
     * never does.
     */
    private static void addToken(Set<String> out, String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) return;
        boolean hasDigit = false;
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                hasDigit = true;
                break;
            }
        }
        if (hasDigit) out.add(token);
    }

    private static String brandName(Product p) {
        return p.getBrand() != null ? p.getBrand().getName() : null;
    }

    // ─────────────────────────────────────────────────────────────
    //  Sitemap reading
    // ─────────────────────────────────────────────────────────────

    /** Product URLs for a host, read once and reused for {@link #CACHE_TTL_MS}. */
    List<String> urlsFor(String host) {
        CachedUrls hit = cache.get(host);
        if (hit != null && !hit.isStale()) return hit.urls;

        List<String> urls = readSitemaps(host);
        cache.put(host, new CachedUrls(urls));
        return urls;
    }

    private List<String> readSitemaps(String host) {
        String root = fetch("https://www." + host + "/sitemap.xml");
        if (root == null || !root.contains("<loc")) {
            root = fetch("https://" + host + "/sitemap.xml");
        }
        if (root == null) {
            log.info("[LinkFinder] {} publishes no readable sitemap", host);
            return List.of();
        }

        List<String> locs = extractLocs(root);
        boolean isIndex = root.toLowerCase(Locale.ROOT).contains("<sitemapindex");
        if (!isIndex) return capped(locs);

        // An index lists other sitemaps. Product ones first, so the useful pages are
        // read even when the per-host budget runs out on blog and category maps.
        List<String> children = new ArrayList<>(locs);
        children.sort((a, b) -> Integer.compare(rank(b), rank(a)));

        List<String> all = new ArrayList<>();
        int fetched = 0;
        for (String child : children) {
            if (fetched >= MAX_CHILD_SITEMAPS || all.size() >= MAX_URLS_PER_HOST) break;
            String body = fetch(child);
            fetched++;
            if (body == null) continue;
            all.addAll(extractLocs(body));
        }
        return capped(all);
    }

    /** Sitemaps whose name mentions products are worth reading before the rest. */
    private static int rank(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("product") || u.contains("urun")) return 2;
        if (u.contains("blog") || u.contains("news") || u.contains("haber")) return 0;
        return 1;
    }

    private static List<String> capped(List<String> urls) {
        return urls.size() <= MAX_URLS_PER_HOST ? urls : urls.subList(0, MAX_URLS_PER_HOST);
    }

    static List<String> extractLocs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = LOC.matcher(xml);
        while (m.find()) {
            String loc = m.group(1).trim()
                    .replace("<![CDATA[", "").replace("]]>", "")
                    .replace("&amp;", "&");
            if (loc.startsWith("http")) out.add(loc);
        }
        return out;
    }

    /**
     * Fetches a sitemap. Returns null rather than throwing: a supplier without a usable
     * sitemap is an ordinary outcome here, not an error worth failing a scan over.
     */
    private String fetch(String url) {
        try {
            URI uri = SsrfGuard.validate(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", ProductImageCrawlerService.userAgent());
            // Sitemaps and product pages both come through here.
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setConnectTimeout(FETCH_TIMEOUT_MS);
            conn.setReadTimeout(FETCH_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) return null;

            boolean gzip = "gzip".equalsIgnoreCase(conn.getContentEncoding())
                    || url.endsWith(".gz");
            try (InputStream raw = conn.getInputStream();
                 InputStream in = gzip ? new GZIPInputStream(raw) : raw;
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buf)) > 0) {
                    total += read;
                    if (total > MAX_SITEMAP_BYTES) return null;
                    out.write(buf, 0, read);
                }
                return out.toString(java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("[LinkFinder] sitemap fetch failed {}: {}", url, e.toString());
            return null;
        }
    }

    /** Upper-case, Turkish letters folded, everything non-alphanumeric dropped. */
    static String normalise(String s) {
        return ProductCrawlBatchService.normalise(s);
    }

    private static final class CachedUrls {
        final List<String> urls;
        final long readAt = System.currentTimeMillis();

        CachedUrls(List<String> urls) {
            this.urls = urls;
        }

        boolean isStale() {
            return System.currentTimeMillis() - readAt > CACHE_TTL_MS;
        }
    }
}
