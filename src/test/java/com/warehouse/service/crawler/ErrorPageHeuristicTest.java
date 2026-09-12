package com.warehouse.service.crawler;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The crawler refuses pages that look like a server error rather than a product. The
 * hard part is that one of those signals also appears on pages that are perfectly fine.
 */
class ErrorPageHeuristicTest {

    @Test
    void aNextErrorShellCarryingProductMetadataIsARealPage() {
        // philips.com.tr serves /c-p/BG3017_01 as <html id="__next_error__"> wrapped
        // around the whole product page. Rejecting it on the marker alone lost every
        // Philips product.
        var doc = Jsoup.parse("""
                <html id="__next_error__"><head>
                <meta property="og:title" content="Bodygroom series 3000"/>
                <meta property="og:image" content="https://images.philips.com/is/image/x.jpg"/>
                </head><body>Bodygroom series 3000</body></html>
                """);

        assertThat(ProductImageCrawlerService.hasProductMetadata(doc)).isTrue();
    }

    @Test
    void anErrorShellWithNothingToDescribeHasNoMetadata() {
        var doc = Jsoup.parse(
                "<html id=\"__next_error__\"><head><title>Error</title></head><body></body></html>");

        assertThat(ProductImageCrawlerService.hasProductMetadata(doc)).isFalse();
    }

    @Test
    void eitherOpenGraphTagOnItsOwnCounts() {
        var onlyImage = Jsoup.parse(
                "<html><head><meta property=\"og:image\" content=\"https://x/y.jpg\"/></head></html>");
        var onlyTitle = Jsoup.parse(
                "<html><head><meta property=\"og:title\" content=\"Bir Ürün\"/></head></html>");
        var blankValues = Jsoup.parse(
                "<html><head><meta property=\"og:image\" content=\"\"/></head></html>");

        assertThat(ProductImageCrawlerService.hasProductMetadata(onlyImage)).isTrue();
        assertThat(ProductImageCrawlerService.hasProductMetadata(onlyTitle)).isTrue();
        assertThat(ProductImageCrawlerService.hasProductMetadata(blankValues)).isFalse();
        assertThat(ProductImageCrawlerService.hasProductMetadata(null)).isFalse();
    }
}
