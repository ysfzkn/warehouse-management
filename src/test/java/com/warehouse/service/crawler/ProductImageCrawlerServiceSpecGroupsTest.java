package com.warehouse.service.crawler;

import com.warehouse.service.crawler.ProductImageCrawlerService.SpecGroup;
import com.warehouse.service.crawler.ProductImageCrawlerService.SpecItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grouped technical-spec extraction tests.
 *
 * Covers the BSH (Profilo/Bosch/Siemens) Next.js flight-data parser — including
 * payloads split across multiple {@code self.__next_f.push} chunks — plus the
 * generic per-table grouped fallback, the JS-string unescaper, and the balanced
 * JSON array scanner.
 */
class ProductImageCrawlerServiceSpecGroupsTest {

    private ProductImageCrawlerService crawler;

    @BeforeEach
    void setUp() {
        // The methods under test don't touch repositories/storage.
        crawler = new ProductImageCrawlerService(null, null, null, null);
    }

    /** The BSH spec JSON exactly as it appears (unescaped) in the flight text. */
    private static final String BSH_SPECS_JSON =
            "{\"headline\":\"X\",\"specifications\":["
            + "{\"name\":\"Genel özellikler\",\"fallbackNameKey\":null,\"key\":\"GENERAL\",\"specifications\":["
            +   "{\"key\":\"COLOR\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Renk\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"Paslanmaz çelik\"},"
            +     "\"requiresValueTranslation\":false,\"unit\":null,\"featureStoryLinkIds\":[]},"
            +   "{\"key\":\"ENERGY\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Yıllık ortalama enerji tüketimi\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"216\"},"
            +     "\"requiresValueTranslation\":false,\"unit\":\"kWh/yıl\",\"featureStoryLinkIds\":[]},"
            +   "{\"key\":\"PRICE_ROW\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Fiyat\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"49.999\"},"
            +     "\"requiresValueTranslation\":false,\"unit\":null,\"featureStoryLinkIds\":[]}"
            + "],\"lineImages\":null},"
            + "{\"name\":\"Soğutucu bölümü\",\"fallbackNameKey\":null,\"key\":\"COOLING\",\"specifications\":["
            +   "{\"key\":\"CAP\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Soğutucu bölümü kapasitesi\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"422\"},"
            +     "\"requiresValueTranslation\":false,\"unit\":\"litre\",\"featureStoryLinkIds\":[]},"
            +   "{\"key\":\"HC\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Home Connect\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"an.yes\"},"
            +     "\"requiresValueTranslation\":true,\"unit\":null,\"featureStoryLinkIds\":[]},"
            +   "{\"key\":\"UNKNOWN\",\"name\":{\"footnoteDataArray\":[],\"text\":\"Bilinmeyen özellik\"},"
            +     "\"value\":{\"footnoteDataArray\":[],\"text\":\"an.someFeatureKey\"},"
            +     "\"requiresValueTranslation\":true,\"unit\":null,\"featureStoryLinkIds\":[]}"
            + "],\"lineImages\":null}]}";

    /** Escapes text the way it sits inside a JS double-quoted string literal. */
    private static String jsEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Document docWithFlightChunks(String... escapedChunks) {
        StringBuilder html = new StringBuilder("<html><head></head><body>");
        for (String chunk : escapedChunks) {
            html.append("<script>self.__next_f.push([1,\"").append(chunk).append("\"])</script>");
        }
        html.append("</body></html>");
        return Jsoup.parse(html.toString());
    }

    @Test
    @DisplayName("BSH flight payload → grouped specs (single chunk)")
    void bshGroups_singleChunk() {
        Document doc = docWithFlightChunks(jsEscape("7:[\"$\",\"div\"," + BSH_SPECS_JSON + "]"));
        List<SpecGroup> groups = crawler.extractSpecGroups(doc);

        assertEquals(2, groups.size());
        assertEquals("Genel özellikler", groups.get(0).title());
        assertEquals("Soğutucu bölümü", groups.get(1).title());

        // Unit appended to the value.
        List<SpecItem> general = groups.get(0).items();
        assertTrue(general.stream().anyMatch(i ->
                i.label().equals("Renk") && i.value().equals("Paslanmaz çelik")));
        assertTrue(general.stream().anyMatch(i ->
                i.label().equals("Yıllık ortalama enerji tüketimi") && i.value().equals("216 kWh/yıl")));
        // Junk filter: pricing rows never pass.
        assertTrue(general.stream().noneMatch(i -> i.label().equals("Fiyat")));

        List<SpecItem> cooling = groups.get(1).items();
        assertTrue(cooling.stream().anyMatch(i ->
                i.label().equals("Soğutucu bölümü kapasitesi") && i.value().equals("422 litre")));
        // an.yes → Evet
        assertTrue(cooling.stream().anyMatch(i ->
                i.label().equals("Home Connect") && i.value().equals("Evet")));
        // Unknown i18n keys are dropped, never shown raw.
        assertTrue(cooling.stream().noneMatch(i -> i.label().equals("Bilinmeyen özellik")));
    }

    @Test
    @DisplayName("BSH flight payload split mid-token across push chunks is reassembled")
    void bshGroups_chunkSplit() {
        String escaped = jsEscape("7:[\"$\",\"div\"," + BSH_SPECS_JSON + "]");
        // Split inside the marker region — between the escaped quote's backslash and quote
        // would be the worst case; cut in the middle of "specifications" to force it.
        int cut = escaped.indexOf("specifications") + 7;
        Document doc = docWithFlightChunks(escaped.substring(0, cut), escaped.substring(cut));

        List<SpecGroup> groups = crawler.extractSpecGroups(doc);
        assertEquals(2, groups.size());
        assertEquals("Genel özellikler", groups.get(0).title());
    }

    @Test
    @DisplayName("Flight text without the group marker yields no BSH groups")
    void bshGroups_noMarker() {
        // Inner item arrays start with {"key": — the marker must not match them.
        String inner = "{\"specifications\":[{\"key\":\"X\",\"name\":{\"text\":\"A\"},\"value\":{\"text\":\"B\"}}]}";
        assertTrue(crawler.parseBshSpecGroups(inner).isEmpty());
        assertTrue(crawler.parseBshSpecGroups("").isEmpty());
        assertTrue(crawler.parseBshSpecGroups(null).isEmpty());
    }

    @Test
    @DisplayName("Generic fallback: tables become groups titled by caption or nearest heading")
    void tableFallback_groupsWithTitles() {
        String html = "<html><body>"
                + "<h3>Boyutlar</h3>"
                + "<table><tr><td>Genişlik</td><td>86 cm</td></tr>"
                + "<tr><td>Derinlik</td><td>81 cm</td></tr></table>"
                + "<table><caption>Kapasite</caption>"
                + "<tr><td>Net hacim</td><td>624 litre</td></tr>"
                + "<tr><td>Dondurucu</td><td>202 litre</td></tr></table>"
                + "</body></html>";
        List<SpecGroup> groups = crawler.extractSpecGroups(Jsoup.parse(html));

        assertEquals(2, groups.size());
        assertEquals("Boyutlar", groups.get(0).title());
        assertEquals("Kapasite", groups.get(1).title());
        assertEquals("86 cm", groups.get(0).items().get(0).value());
    }

    @Test
    @DisplayName("Tables with fewer than 2 spec rows are skipped (layout tables)")
    void tableFallback_skipsLayoutTables() {
        String html = "<html><body><table><tr><td>Tek satır</td><td>değer</td></tr></table></body></html>";
        assertTrue(crawler.extractSpecGroups(Jsoup.parse(html)).isEmpty());
    }

    @Test
    @DisplayName("Philips-style dl lists become groups titled by class-based headings")
    void dlFallback_philipsStructure() {
        String html = "<html><body><ul>"
                + "<li><p class=\"p-heading-03 p-s08__spec-title\">Menşei</p>"
                + "<dl><dt>Üretildiği Yer:</dt><dd>Çin</dd></dl></li>"
                + "<li><p class=\"p-heading-03 p-s08__spec-title\">Genel özellikler</p>"
                + "<dl><dt>Güç</dt><dd>1400 W</dd><dt>Kapasite</dt><dd>4.1 L</dd></dl></li>"
                + "</ul></body></html>";
        List<SpecGroup> groups = crawler.extractSpecGroups(Jsoup.parse(html));

        assertEquals(2, groups.size());
        assertEquals("Menşei", groups.get(0).title());
        assertEquals("Genel özellikler", groups.get(1).title());
        assertEquals("Çin", groups.get(0).items().get(0).value());
        assertEquals(2, groups.get(1).items().size());
    }

    @Test
    @DisplayName("Consecutive dl lists with the same resolved title are merged")
    void dlFallback_mergesSameTitle() {
        String html = "<html><body>"
                + "<h3>Teknik</h3>"
                + "<dl><dt>Güç</dt><dd>1400 W</dd></dl>"
                + "<dl><dt>Kapasite</dt><dd>4.1 L</dd></dl>"
                + "</body></html>";
        List<SpecGroup> groups = crawler.extractSpecGroups(Jsoup.parse(html));

        assertEquals(1, groups.size());
        assertEquals("Teknik", groups.get(0).title());
        assertEquals(2, groups.get(0).items().size());
    }

    @Test
    @DisplayName("unescapeJsString handles quotes, backslashes and unicode escapes")
    void unescape() {
        assertEquals("a\"b", ProductImageCrawlerService.unescapeJsString("a\\\"b"));
        assertEquals("a\\b", ProductImageCrawlerService.unescapeJsString("a\\\\b"));
        assertEquals("a\nb", ProductImageCrawlerService.unescapeJsString("a\\nb"));
        assertEquals("ç", ProductImageCrawlerService.unescapeJsString("\\u00e7"));
        assertEquals("plain", ProductImageCrawlerService.unescapeJsString("plain"));
    }

    @Test
    @DisplayName("extractBalancedArray respects nesting and string literals containing brackets")
    void balancedArray() {
        String text = "x[1,[2,3],{\"a\":\"]tricky[\"},\"\\\"esc\\\"\"]y";
        assertEquals("[1,[2,3],{\"a\":\"]tricky[\"},\"\\\"esc\\\"\"]",
                ProductImageCrawlerService.extractBalancedArray(text, 1));
        assertNull(ProductImageCrawlerService.extractBalancedArray("[1,2", 0));
        assertNull(ProductImageCrawlerService.extractBalancedArray("abc", 0));
    }
}
