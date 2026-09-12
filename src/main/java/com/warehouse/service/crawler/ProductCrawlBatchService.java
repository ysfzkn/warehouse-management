package com.warehouse.service.crawler;

import com.warehouse.entity.Product;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.crawler.ProductImageCrawlerService.CrawlException;
import com.warehouse.service.crawler.ProductImageCrawlerService.CrawlPreview;
import com.warehouse.service.crawler.ProductImageCrawlerService.ImportResult;
import com.warehouse.service.crawler.ProductImageCrawlerService.SpecGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bulk counterpart of {@link ProductImageCrawlerService}: the admin pastes a list of
 * supplier product URLs, and each one is crawled and matched against a product in the
 * catalogue so photos and copy can be imported in a single pass.
 *
 * <h3>Why this runs as a job instead of a plain request</h3>
 * A URL cannot be matched before its page has been read — the model code and title that
 * identify the product exist only in the fetched HTML. Fetching is deliberately paced
 * ({@link #FETCH_SPACING_MS}) so a batch does not hammer a supplier, which puts a
 * fifty-link list well past any sane HTTP timeout. {@link #startMatch} therefore returns
 * a job id immediately and the UI polls {@link #getJob}.
 *
 * <p>Jobs live in memory: they are disposable progress for one admin sitting in front of
 * the screen, worthless after a restart and never a source of truth.
 */
@Service
public class ProductCrawlBatchService {

    private static final Logger log = LoggerFactory.getLogger(ProductCrawlBatchService.class);

    /** Enough for a supplier catalogue page's worth of links; keeps one job bounded. */
    public static final int MAX_URLS = 50;
    /** Politeness gap between two third-party page fetches. */
    private static final long FETCH_SPACING_MS = 1200;
    private static final long JOB_TTL_MS = 60 * 60 * 1000L;

    /**
     * A stock code shorter than this matches far too much once punctuation is stripped
     * ("AL" would hit half the catalogue), so short codes fall through to name scoring.
     */
    private static final int MIN_SKU_LENGTH = 4;
    /** Share of a product's name tokens that must appear in the page title. */
    private static final double NAME_MATCH_THRESHOLD = 0.6;
    private static final int MAX_CANDIDATES = 3;

    private final ProductImageCrawlerService crawler;
    private final ProductRepository productRepository;
    private final SupplierLinkFinder linkFinder;

    private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crawl-batch");
        t.setDaemon(true);
        return t;
    });

    public ProductCrawlBatchService(ProductImageCrawlerService crawler,
                                    ProductRepository productRepository,
                                    SupplierLinkFinder linkFinder) {
        this.crawler = crawler;
        this.productRepository = productRepository;
        this.linkFinder = linkFinder;
    }

    // ─────────────────────────────────────────────────────────────
    //  Match phase
    // ─────────────────────────────────────────────────────────────

    /** Queues the crawl-and-match run and hands back the id the UI polls. */
    public String startMatch(List<String> rawUrls) {
        List<String> urls = normaliseUrlList(rawUrls);
        if (urls.isEmpty()) {
            throw new CrawlException("Geçerli bir bağlantı bulunamadı.");
        }
        if (urls.size() > MAX_URLS) {
            throw new CrawlException(
                    "En fazla " + MAX_URLS + " bağlantı işlenebilir. Listeyi bölerek deneyin.");
        }

        sweep();
        String jobId = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(jobId, urls);
        jobs.put(jobId, job);
        worker.submit(() -> runMatch(job));
        return jobId;
    }

    /**
     * Splits pasted text into URLs. The admin copies from a spreadsheet or a chat
     * window, so links arrive separated by newlines, commas or plain spaces, sometimes
     * with an index or a bullet in front, and duplicates are common.
     */
    static List<String> normaliseUrlList(Collection<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String chunk : raw) {
            if (chunk == null) continue;
            for (String piece : chunk.split("[\\s,;]+")) {
                int at = piece.indexOf("http");
                if (at < 0) continue;
                String url = piece.substring(at).trim();
                // Trailing punctuation carried over from prose must not reach the fetcher.
                while (!url.isEmpty() && TRAILING_JUNK.indexOf(url.charAt(url.length() - 1)) >= 0) {
                    url = url.substring(0, url.length() - 1);
                }
                if (url.length() > 10 && !out.contains(url)) out.add(url);
            }
        }
        return out;
    }

    private static final String TRAILING_JUNK = ")]}>,.;\"'";

    // ─────────────────────────────────────────────────────────────
    //  Discovery: find the links instead of being handed them
    // ─────────────────────────────────────────────────────────────

    /**
     * Scans the storefront for products with no photo at all and looks each one up on
     * its manufacturer's site, then crawls whatever it found.
     *
     * <p>The empty grey box on a product page is the thing being fixed here, so the
     * search set is exactly "visible in the shop, zero images". Everything after the
     * lookup is the ordinary pasted-link flow, which means the crawl still has to agree
     * that the page belongs to that product before anything is written.
     */
    public String startDiscovery(int limit) {
        sweep();
        String jobId = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(jobId);
        job.phase = "DISCOVER";
        jobs.put(jobId, job);
        int capped = limit > 0 ? Math.min(limit, MAX_URLS) : MAX_URLS;
        worker.submit(() -> runDiscovery(job, capped));
        return jobId;
    }

    private void runDiscovery(BatchJob job, int limit) {
        List<Product> targets;
        try {
            targets = productRepository.findEcommerceProductsWithoutImages();
        } catch (Exception e) {
            log.error("[CrawlBatch] photoless product scan failed", e);
            job.state = "FAILED";
            job.error = "Fotoğrafsız ürünler okunamadı.";
            return;
        }
        if (targets.size() > limit) targets = targets.subList(0, limit);
        job.plannedTotal = targets.size();

        for (Product p : targets) {
            try {
                String url = linkFinder.find(p);
                if (url != null) {
                    BatchItem item = new BatchItem(url);
                    item.discoveredForProductId = p.getId();
                    item.discoveredForProductName = p.getName();
                    item.discoveredForProductSku = p.getSku();
                    job.items.add(item);
                }
            } catch (Exception e) {
                log.warn("[CrawlBatch] link lookup failed for {}: {}", p.getSku(), e.toString());
            } finally {
                job.processed.incrementAndGet();
            }
        }

        // Second half of the run reuses the pasted-link pipeline verbatim.
        job.phase = "CRAWL";
        job.processed.set(0);
        if (job.items.isEmpty()) {
            job.state = "DONE";
            return;
        }
        runMatch(job);
    }

    private void runMatch(BatchJob job) {
        List<Product> catalogue;
        try {
            catalogue = productRepository.findAll();
        } catch (Exception e) {
            log.error("[CrawlBatch] catalogue load failed", e);
            job.state = "FAILED";
            job.error = "Ürün listesi okunamadı.";
            return;
        }

        for (int i = 0; i < job.items.size(); i++) {
            BatchItem item = job.items.get(i);
            try {
                if (i > 0) Thread.sleep(FETCH_SPACING_MS);
                CrawlPreview preview = crawler.preview(item.url);
                item.title = preview.title();
                item.brand = preview.brand();
                item.images = preview.images() != null ? preview.images() : List.of();
                item.description = preview.description();
                item.shortDescription = preview.shortDescription();
                item.specGroups = toSpecMaps(preview.specGroups());
                List<Candidate> titleMatches = match(preview.title(), item.url, catalogue);
                item.candidates = withDiscovered(item, titleMatches);
                if (item.candidates.isEmpty()) {
                    item.status = "NO_MATCH";
                    item.message = "Bu sayfa hiçbir ürünle eşleşmedi — ürünü elle seçin.";
                } else if (contradicts(item, titleMatches)) {
                    // The link was found from this product's stock code, but the page
                    // itself identifies a different product by its own code. One of the
                    // two is wrong and nothing is pre-ticked until a human says which.
                    item.status = "CONFLICT";
                    item.message = "Otomatik bulunan sayfa başka bir ürüne işaret ediyor — kontrol edin.";
                    item.productId = null;
                } else {
                    item.status = "OK";
                    item.productId = item.candidates.get(0).productId();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                item.status = "ERROR";
                item.message = "İşlem durduruldu.";
                break;
            } catch (CrawlException ce) {
                item.status = "ERROR";
                item.message = ce.getMessage();
            } catch (Exception e) {
                log.warn("[CrawlBatch] {} failed: {}", item.url, e.toString());
                item.status = "ERROR";
                item.message = "Sayfa okunamadı.";
            } finally {
                job.processed.incrementAndGet();
            }
        }
        if (!"FAILED".equals(job.state)) job.state = "DONE";
    }

    // ─────────────────────────────────────────────────────────────
    //  Matching
    // ─────────────────────────────────────────────────────────────

    /**
     * Ranks catalogue products against a crawled page title.
     *
     * <p>The stock code is the strong signal: suppliers put the model code in the page
     * title ("HF 3E53E0W-17 | Bulaşık makineleri"), and once both sides are stripped to
     * bare alphanumerics it either occurs verbatim or it does not. Name overlap is the
     * fallback for products whose stock code is internal and appears nowhere upstream.
     */
    List<Candidate> match(String pageTitle, List<Product> catalogue) {
        return match(pageTitle, null, catalogue);
    }

    /**
     * @param pageUrl the address the page was read from, searched alongside the title
     */
    List<Candidate> match(String pageTitle, String pageUrl, List<Product> catalogue) {
        String haystack = normalise(pageTitle);
        String address = normalise(pageUrl);
        if (haystack.isEmpty() && address.isEmpty()) return List.of();

        List<Candidate> found = new ArrayList<>();
        for (Product p : catalogue) {
            String sku = normalise(p.getSku());
            if (sku.length() >= MIN_SKU_LENGTH && haystack.contains(sku)) {
                found.add(new Candidate(p.getId(), p.getName(), p.getSku(), 100, "Stok kodu"));
                continue;
            }
            // Stock codes are written "Philips BG3017", so the full string almost never
            // appears anywhere upstream — the brand sits elsewhere in the title, or the
            // model lives only in the address (philips.com.tr titles a page "Airfryer"
            // and serves it at /c-p/HD9285_96). Searching the brand-stripped code in
            // both places is what actually identifies these products. The token rules
            // are the link finder's: at least four characters and a digit, so a colour
            // or category word can never match here.
            if (matchingCode(p, haystack, address) != null) {
                found.add(new Candidate(p.getId(), p.getName(), p.getSku(), 95, "Model kodu"));
                continue;
            }
            double ratio = nameOverlap(p.getName(), haystack);
            if (ratio >= NAME_MATCH_THRESHOLD) {
                found.add(new Candidate(p.getId(), p.getName(), p.getSku(),
                        (int) Math.round(ratio * 90), "Ürün adı"));
            }
        }
        found.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return found.size() > MAX_CANDIDATES
                ? new ArrayList<>(found.subList(0, MAX_CANDIDATES))
                : found;
    }

    /**
     * True when a discovered link's page names a different product by its stock code.
     *
     * <p>Only a stock-code hit counts as contradiction. Name overlap is far too loose to
     * overrule a discovery: supplier titles routinely read "Bulaşık makineleri | Hoover",
     * which resembles half the catalogue and would flag every row.
     */
    private static boolean contradicts(BatchItem item, List<Candidate> titleMatches) {
        if (item.discoveredForProductId == null) return false;
        for (Candidate c : titleMatches) {
            if (c.score() == 100 && !c.productId().equals(item.discoveredForProductId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the product a discovered link was looked up for at the head of the list.
     *
     * <p>The link was found *because* this product's stock code is in its URL, which is
     * stronger evidence than anything the page title can offer — supplier titles often
     * read "Bulaşık makineleri | Hoover" and match nothing. The title-based candidates
     * are kept behind it so the admin can still overrule the choice.
     */
    private static List<Candidate> withDiscovered(BatchItem item, List<Candidate> titleMatches) {
        if (item.discoveredForProductId == null) return titleMatches;
        List<Candidate> out = new ArrayList<>();
        out.add(new Candidate(item.discoveredForProductId, item.discoveredForProductName,
                item.discoveredForProductSku, 95, "Otomatik bulundu"));
        for (Candidate c : titleMatches) {
            if (!c.productId().equals(item.discoveredForProductId)) out.add(c);
        }
        return out;
    }

    /**
     * The product's model code if the title or the address contains it, else null.
     *
     * <p>Uses the same token rules as the supplier link finder, so "Philips BG3017"
     * offers BG3017 and never the brand on its own. Tokens come longest first: on a page
     * served at /c-p/43PUS8007_62 both "43PUS800762" and a bare "8007" would hit, and
     * the more specific one should decide.
     */
    private String matchingCode(Product product, String title, String address) {
        if (linkFinder == null) return null;
        for (String token : linkFinder.codeTokens(product)) {
            if (title.contains(token) || address.contains(token)) return token;
        }
        return null;
    }

    /** Fraction of the product name's meaningful tokens that occur in the page title. */
    private static double nameOverlap(String productName, String normalisedTitle) {
        if (productName == null || productName.isBlank()) return 0;
        int meaningful = 0;
        int hits = 0;
        for (String token : productName.split("\\s+")) {
            String t = normalise(token);
            // One- and two-character fragments ("D", "RD") carry no evidence on their own.
            if (t.length() < 3) continue;
            meaningful++;
            if (normalisedTitle.contains(t)) hits++;
        }
        // A single matching token is a coincidence, not an identification.
        if (meaningful < 2) return 0;
        return (double) hits / meaningful;
    }

    /** Upper-case, Turkish letters folded to ASCII, everything non-alphanumeric dropped. */
    static String normalise(String s) {
        if (s == null) return "";
        String upper = s.toUpperCase(new Locale("tr", "TR"));
        StringBuilder sb = new StringBuilder(upper.length());
        for (char c : upper.toCharArray()) {
            char folded = switch (c) {
                case 'İ', 'I', 'Ì', 'Í' -> 'I';
                case 'Ş' -> 'S';
                case 'Ğ' -> 'G';
                case 'Ü' -> 'U';
                case 'Ö' -> 'O';
                case 'Ç' -> 'C';
                default -> c;
            };
            if (Character.isLetterOrDigit(folded)) sb.append(folded);
        }
        return sb.toString();
    }

    private static List<Map<String, Object>> toSpecMaps(List<SpecGroup> groups) {
        if (groups == null || groups.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpecGroup g : groups) {
            List<Map<String, Object>> items = new ArrayList<>();
            if (g.items() != null) {
                for (var it : g.items()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", it.label());
                    row.put("value", it.value());
                    items.add(row);
                }
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("title", g.title());
            group.put("items", items);
            out.add(group);
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────
    //  Apply phase
    // ─────────────────────────────────────────────────────────────

    /**
     * Imports the rows the admin ticked. Photos are appended rather than swapped in, so
     * a mis-matched row costs an unwanted extra picture instead of destroying the
     * product's existing gallery. Copy fields are written only when the source actually
     * produced something.
     *
     * <p>Deliberately not one transaction: twenty products are updated here, each one
     * independent, and a single bad row should not roll back the nineteen that worked.
     * Failures are collected and reported per row.
     */
    public ApplySummary apply(String jobId, List<ApplyRequestItem> approved) {
        BatchJob job = jobs.get(jobId);
        if (job == null) {
            throw new CrawlException("Bu toplu işlem artık geçerli değil. Listeyi yeniden eşleştirin.");
        }
        if (approved == null || approved.isEmpty()) {
            throw new CrawlException("Onaylanan satır yok.");
        }

        int applied = 0;
        int photos = 0;
        List<String> errors = new ArrayList<>();

        for (ApplyRequestItem req : approved) {
            if (req == null || req.url == null || req.productId == null) continue;
            BatchItem item = job.items.stream()
                    .filter(i -> i.url.equals(req.url))
                    .findFirst().orElse(null);
            if (item == null) continue;

            try {
                if (!item.images.isEmpty()) {
                    ImportResult result = crawler.importImages(
                            req.productId, item.images, false, false, item.url);
                    photos += result.success();
                    errors.addAll(result.errors());
                }
                applyCopy(req.productId, item);
                item.status = "APPLIED";
                applied++;
            } catch (Exception e) {
                log.warn("[CrawlBatch] apply failed for {}: {}", req.url, e.toString());
                String reason = readable(e.getMessage());
                errors.add(label(item) + ": " + reason);
                item.status = "APPLY_ERROR";
                item.message = reason;
            }
        }
        return new ApplySummary(applied, photos, errors);
    }

    private void applyCopy(Long productId, BatchItem item) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return;

        boolean touched = false;
        if (isFilled(item.description)) {
            product.setDescription(item.description);
            touched = true;
        }
        if (isFilled(item.shortDescription)) {
            // The column stops at 1000 characters; a longer summary would fail the
            // insert and take the whole row's import down with it.
            product.setShortDescription(trim(item.shortDescription, 1000));
            touched = true;
        }
        if (item.specGroups != null && !item.specGroups.isEmpty()) {
            product.setTechnicalSpecs(item.specGroups);
            touched = true;
        }
        if (touched) productRepository.save(product);
    }

    private static String label(BatchItem item) {
        String t = item.title;
        if (t == null || t.isBlank()) return item.url;
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    /**
     * Turns an exception message into something safe to show an admin.
     *
     * <p>The message travels from here to the browser, and a failure deep in an HTTP
     * client can carry an entire error page in its text. Rendering that put a screenful
     * of "&lt;!DOCTYPE html&gt;… nginx" markup in place of a sentence, so tags are
     * stripped and the result is capped.
     */
    private static String readable(String raw) {
        if (raw == null || raw.isBlank()) return "Bilinmeyen hata.";
        String text = raw.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return "Bilinmeyen hata.";
        return trim(text, 160);
    }

    private static boolean isFilled(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ─────────────────────────────────────────────────────────────
    //  Job access
    // ─────────────────────────────────────────────────────────────

    public BatchJob getJob(String jobId) {
        BatchJob job = jobs.get(jobId);
        if (job == null) {
            throw new CrawlException("Toplu işlem bulunamadı veya süresi doldu.");
        }
        return job;
    }

    /** Drops jobs older than the TTL so an admin's afternoon does not leak memory. */
    private void sweep() {
        long cutoff = System.currentTimeMillis() - JOB_TTL_MS;
        jobs.entrySet().removeIf(e -> e.getValue().createdAt.toEpochMilli() < cutoff);
    }

    // ── Models ──

    public static class BatchJob {
        public final String id;
        public final Instant createdAt = Instant.now();
        /**
         * Copy-on-write because a discovery run appends to this list from the worker
         * thread while the UI is polling it from request threads.
         */
        public final List<BatchItem> items = new java.util.concurrent.CopyOnWriteArrayList<>();
        public final AtomicInteger processed = new AtomicInteger();
        public volatile String state = "RUNNING";
        /** DISCOVER while links are being looked up, CRAWL while pages are read. */
        public volatile String phase = "CRAWL";
        /** Known up front for discovery, where items appear as they are found. */
        public volatile int plannedTotal;
        public volatile String error;

        BatchJob(String id, List<String> urls) {
            this.id = id;
            for (String u : urls) items.add(new BatchItem(u));
            this.plannedTotal = items.size();
        }

        BatchJob(String id) {
            this.id = id;
        }

        public int total() {
            return "DISCOVER".equals(phase) ? plannedTotal : items.size();
        }
    }

    public static class BatchItem {
        public final String url;
        /** Set when the link was found for a known product rather than pasted by hand. */
        public volatile Long discoveredForProductId;
        public volatile String discoveredForProductName;
        public volatile String discoveredForProductSku;
        public volatile String status = "PENDING";
        public volatile String message;
        public volatile String title;
        public volatile String brand;
        public volatile Long productId;
        public volatile List<String> images = List.of();
        public volatile String description;
        public volatile String shortDescription;
        public volatile List<Map<String, Object>> specGroups = List.of();
        public volatile List<Candidate> candidates = List.of();

        BatchItem(String url) {
            this.url = url;
        }
    }

    /** One possible product for a crawled page, plus why it was proposed. */
    public record Candidate(Long productId, String productName, String sku, int score, String reason) {}

    public record ApplySummary(int applied, int photos, List<String> errors) {}

    public static class ApplyRequestItem {
        public String url;
        public Long productId;
    }
}
