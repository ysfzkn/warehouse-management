package com.warehouse.service.seo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fragments are pasted into index.html by nginx, outside React. Nothing downstream
 * escapes them: a product named {@code </title><script>…} would run in every visitor's
 * browser on that product's page. And the tags must carry {@code data-rh}, or the SPA adds
 * a second description and canonical next to the server's instead of replacing them.
 */
class SeoFragmentRendererTest {

    private static final String HOSTILE = "Buzdolabı \"A+\" </title><script>alert(1)</script>";

    private static SeoPage page(String title, String heading, List<SeoPage.Link> links) {
        return new SeoPage(title, HOSTILE, "https://atsdtm.com.tr/urun/x", "", "product",
                "ATS DTM", heading, HOSTILE, "Ürünler", links);
    }

    @Test
    @DisplayName("Yönetici girdisi head içinde kaçışlanır, etiket kapatılamaz")
    void staffTypedValuesCannotBreakOutOfTheHead() {
        String head = SeoFragmentRenderer.head(page(HOSTILE, "x", List.of()));

        assertThat(head)
                .as("ham <script> head'e girerse her ziyaretçide çalışır")
                .doesNotContain("<script>")
                .doesNotContain("</title><")
                .contains("&lt;script&gt;")
                .contains("content=\"Buzdolabı &quot;A+&quot;");
    }

    @Test
    @DisplayName("Yönetici girdisi body içinde kaçışlanır, bağlantı href'i dahil")
    void staffTypedValuesCannotBreakOutOfTheBody() {
        String body = SeoFragmentRenderer.body(page("t", HOSTILE,
                List.of(new SeoPage.Link(HOSTILE, "/urun/\"><script>x</script>"))));

        assertThat(body).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Head etiketleri data-rh taşır ki SPA onları değiştirsin, çoğaltmasın")
    void headTagsAreMarkedForHelmetToReplace() {
        String head = SeoFragmentRenderer.head(page("Başlık", "x", List.of()));

        assertThat(head)
                .contains("<link rel=\"canonical\" href=\"https://atsdtm.com.tr/urun/x\" data-rh=\"true\">")
                .containsPattern("<meta name=\"description\" content=\"[^\"]*\" data-rh=\"true\">");
    }

    @Test
    @DisplayName("Boş görsel og:image üretmez, twitter kartı küçük kalır")
    void noImageMeansNoOgImage() {
        String head = SeoFragmentRenderer.head(page("Başlık", "x", List.of()));

        assertThat(head).doesNotContain("og:image").contains("content=\"summary\"");
    }
}
