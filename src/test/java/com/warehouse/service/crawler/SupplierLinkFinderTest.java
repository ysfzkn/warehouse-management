package com.warehouse.service.crawler;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The finder decides which supplier page a photoless product is filled from, so a loose
 * token here quietly attaches another model's photos to it. The cases below use real
 * stock-code shapes from the catalogue.
 */
class SupplierLinkFinderTest {

    private final SupplierLinkFinder finder = new SupplierLinkFinder();

    private static Product product(String brand, String sku) {
        Product p = new Product();
        if (brand != null) {
            Brand b = new Brand();
            b.setName(brand);
            p.setBrand(b);
        }
        p.setSku(sku);
        return p;
    }

    // ── Brand to supplier host ──

    @Test
    void mapsBrandsToTheirOwnCatalogue() {
        assertThat(finder.hostForBrand("Simfer")).isEqualTo("simfer.com.tr");
        assertThat(finder.hostForBrand("Hoover")).isEqualTo("hoover-home.com");
        assertThat(finder.hostForBrand("Profilo")).isEqualTo("profilo.com");
    }

    @Test
    void matchesABrandWhoseSiteAddsAWord() {
        // The brand is "Ferre"; the site is ferreturkiye.com.
        assertThat(finder.hostForBrand("Ferre")).isEqualTo("ferreturkiye.com");
    }

    @Test
    void returnsNothingForBrandsWeCannotCrawl() {
        assertThat(finder.hostForBrand("Marsstar")).isNull();
        assertThat(finder.hostForBrand("Altus")).isNull();
        assertThat(finder.hostForBrand("")).isNull();
        assertThat(finder.hostForBrand(null)).isNull();
    }

    // ── Stock code tokens ──

    @Test
    void dropsTheBrandFromTheCodeBecauseEveryUrlOnItsSiteContainsIt() {
        var tokens = finder.codeTokens(product("Simfer", "Simfer 40 SFSW4M"));

        assertThat(tokens).contains("40SFSW4M", "SFSW4M");
        assertThat(tokens).doesNotContain("SIMFER");
    }

    @Test
    void keepsTheModelCodeWithItsSeparatorsStripped() {
        // Slugs write it as "simfer-sr-2515-…", which normalises to the same string.
        var tokens = finder.codeTokens(product("Simfer", "SR-2515"));

        assertThat(tokens).contains("SR2515");
    }

    @Test
    void ordersTokensLongestFirstSoTheMostSpecificWins() {
        var tokens = finder.codeTokens(product("Hoover", "HF 3E53E0W-17"));

        assertThat(tokens).isSortedAccordingTo((a, b) -> Integer.compare(b.length(), a.length()));
        assertThat(tokens.get(0)).isEqualTo("HF3E53E0W17");
    }

    @Test
    void refusesFragmentsTooShortToIdentifyAModel() {
        var tokens = finder.codeTokens(product("Altus", "AL 11"));

        // "AL" and "11" on their own would appear in a large share of any catalogue's
        // slugs, so neither survives. Joined they are specific enough to try — and a
        // weak hit is still re-checked against the fetched page before anything is
        // written, see ProductCrawlBatchService#contradicts.
        assertThat(tokens).doesNotContain("AL", "11");
        assertThat(tokens).containsExactly("AL11");
    }

    @Test
    void handlesAMissingBrandOrCode() {
        assertThat(finder.codeTokens(product(null, "SR-2515"))).contains("SR2515");
        assertThat(finder.codeTokens(product("Simfer", null))).isEmpty();
        assertThat(finder.codeTokens(product("Simfer", "   "))).isEmpty();
    }

    @Test
    void findsNothingWhenTheBrandHasNoSupportedSite() {
        assertThat(finder.find(product("Marsstar", "MS-210"))).isNull();
        assertThat(finder.find(null)).isNull();
    }

    // ── Sitemap parsing ──

    @Test
    void readsLocationsOutOfASitemap() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset><url><loc>https://simfer.com.tr/simfer-sr-2515-cift-kapili-buzdolabi</loc></url>
                <url><loc><![CDATA[https://simfer.com.tr/simfer-4502-beyaz]]></loc></url>
                <url><loc>  https://simfer.com.tr/a?x=1&amp;y=2  </loc></url>
                <url><loc>/gecersiz-gorece-yol</loc></url></urlset>
                """;

        var locs = SupplierLinkFinder.extractLocs(xml);

        assertThat(locs).containsExactly(
                "https://simfer.com.tr/simfer-sr-2515-cift-kapili-buzdolabi",
                "https://simfer.com.tr/simfer-4502-beyaz",
                "https://simfer.com.tr/a?x=1&y=2");
    }

    @Test
    void survivesRubbishInsteadOfXml() {
        assertThat(SupplierLinkFinder.extractLocs("")).isEmpty();
        assertThat(SupplierLinkFinder.extractLocs("<html>404</html>")).isEmpty();
    }
}
