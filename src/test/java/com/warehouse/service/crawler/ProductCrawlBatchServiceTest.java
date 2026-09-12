package com.warehouse.service.crawler;

import com.warehouse.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matcher decides which product a pasted supplier link ends up writing to, so a
 * wrong answer here quietly puts one product's photos and copy onto another. These
 * cases use real page titles from the suppliers the crawler is allowed to read.
 */
class ProductCrawlBatchServiceTest {

    private final ProductCrawlBatchService service =
            new ProductCrawlBatchService(null, null, new SupplierLinkFinder());

    private static Product branded(long id, String name, String sku, String brand) {
        Product p = product(id, name, sku);
        com.warehouse.entity.Brand b = new com.warehouse.entity.Brand();
        b.setName(brand);
        p.setBrand(b);
        return p;
    }

    private static Product product(long id, String name, String sku) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setSku(sku);
        return p;
    }

    // ── URL list parsing ──

    @Test
    void splitsPastedTextOnNewlinesAndPunctuation() {
        List<String> urls = ProductCrawlBatchService.normaliseUrlList(List.of(
                "https://a.com/one\nhttps://a.com/two, https://a.com/three"));
        assertThat(urls).containsExactly(
                "https://a.com/one", "https://a.com/two", "https://a.com/three");
    }

    @Test
    void dropsDuplicatesAndTrailingPunctuation() {
        List<String> urls = ProductCrawlBatchService.normaliseUrlList(List.of(
                "https://a.com/x).", "https://a.com/x", "sadece metin"));
        assertThat(urls).containsExactly("https://a.com/x");
    }

    @Test
    void keepsOnlyThingsThatLookLikeLinks() {
        assertThat(ProductCrawlBatchService.normaliseUrlList(List.of("1. bak buraya", ""))).isEmpty();
        assertThat(ProductCrawlBatchService.normaliseUrlList(null)).isEmpty();
    }

    // ── Matching ──

    @Test
    void matchesOnStockCodeEvenWhenTheTitleSpacesItDifferently() {
        var catalogue = List.of(
                product(1, "Hoover Bulaşık Makinesi", "HF3E53E0W-17"),
                product(2, "Simfer Ankastre Fırın", "40SFSW4M"));

        var hits = service.match("HF 3E53E0W-17 | Bulaşık makineleri | Hoover", catalogue);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).productId()).isEqualTo(1L);
        assertThat(hits.get(0).reason()).isEqualTo("Stok kodu");
        assertThat(hits.get(0).score()).isEqualTo(100);
    }

    @Test
    void ignoresStockCodesTooShortToIdentifyAnything() {
        // "AL" survives normalisation and would otherwise hit any title containing it.
        var catalogue = List.of(product(1, "Zzz", "AL"));

        assertThat(service.match("ALTUS Yağlı Radyatör", catalogue)).isEmpty();
    }

    @Test
    void fallsBackToProductNameWhenTheStockCodeIsInternal() {
        var catalogue = List.of(product(1, "Yağlı Radyatör Beyaz", "INT-90881"));

        var hits = service.match("AL 11 RD | Yağlı Radyatör Beyaz | ALTUS", catalogue);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).reason()).isEqualTo("Ürün adı");
    }

    @Test
    void doesNotMatchOnASingleSharedWord() {
        // "Radyatör" alone is shared by half the catalogue and identifies nothing.
        var catalogue = List.of(product(1, "Radyatör", "INT-1"));

        assertThat(service.match("AL 11 RD | Yağlı Radyatör | ALTUS", catalogue)).isEmpty();
    }

    @Test
    void foldsTurkishLettersSoCasingNeverDecidesAMatch() {
        var catalogue = List.of(product(1, "ÇAMAŞIR MAKİNESİ İNVERTER", "INT-2"));

        var hits = service.match("Çamaşır Makinesi İnverter | Marka", catalogue);

        assertThat(hits).hasSize(1);
    }

    @Test
    void rankshigherScoringCandidatesFirstAndCapsTheList() {
        var catalogue = List.of(
                product(1, "Bulaşık Makinesi Beyaz Ankastre", "INT-1"),
                product(2, "Hoover Bulaşık Makinesi", "HF3E53E0W-17"),
                product(3, "Bulaşık Makinesi Beyaz", "INT-3"),
                product(4, "Bulaşık Makinesi Ankastre Gri", "INT-4"));

        var hits = service.match("HF 3E53E0W-17 Bulaşık Makinesi Beyaz | Hoover", catalogue);

        assertThat(hits).hasSizeLessThanOrEqualTo(3);
        assertThat(hits.get(0).productId()).isEqualTo(2L);
        assertThat(hits).isSortedAccordingTo(
                (a, b) -> Integer.compare(b.score(), a.score()));
    }

    // ── Matching on the address ──

    @Test
    void findsTheModelInTheAddressWhenTheTitleOmitsIt() {
        // philips.com.tr titles this page "Airfryer" and nothing else; the model only
        // exists in the URL it was served from.
        var catalogue = List.of(branded(1, "5000 Serisi XXL Connected", "Philips 9285", "Philips"));

        var hits = service.match("Airfryer", "https://www.philips.com.tr/c-p/HD9285_96", catalogue);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).reason()).isEqualTo("Model kodu");
    }

    @Test
    void findsABrandPrefixedCodeInTheTitleToo() {
        // "Philips BG3017" never appears verbatim upstream — the brand sits at the other
        // end of the title — so the brand-stripped code is what has to be looked for.
        var catalogue = List.of(branded(1, "Bir Ürün", "Philips BG3017", "Philips"));

        var hits = service.match("BG3017/00 Bodygroom series 3000 | Philips", null, catalogue);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).reason()).isEqualTo("Model kodu");
    }

    @Test
    void aVerbatimStockCodeInTheTitleStillScoresHighest() {
        var catalogue = List.of(product(1, "Bir Ürün", "HF3E53E0W-17"));

        var hits = service.match("HF 3E53E0W-17 | Hoover", null, catalogue);

        assertThat(hits.get(0).reason()).isEqualTo("Stok kodu");
        assertThat(hits.get(0).score()).isEqualTo(100);
    }

    @Test
    void theBrandNameInTheAddressIsNotAMatch() {
        // Every page on philips.com.tr contains "philips"; only the model may decide.
        var catalogue = List.of(branded(1, "Rastgele Ürün", "Philips", "Philips"));

        assertThat(service.match("", "https://www.philips.com.tr/c-p/HD9285_96", catalogue)).isEmpty();
    }

    @Test
    void returnsNothingForAnEmptyTitle() {
        assertThat(service.match("", List.of(product(1, "Bir Ürün", "SKU1234")))).isEmpty();
        assertThat(service.match(null, List.of(product(1, "Bir Ürün", "SKU1234")))).isEmpty();
    }
}
