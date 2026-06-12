package com.warehouse.service.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Manual smoke test against a locally saved Profilo product page
 * (-Dprofilo.html=C:\path\to\profilo.html). Prints the extracted groups.
 * Skipped unless the property is set, so CI never depends on external HTML.
 */
class ProfiloLivePageManualTest {

    @Test
    @EnabledIfSystemProperty(named = "profilo.html", matches = ".+")
    void printGroupsFromSavedPage() throws Exception {
        File f = new File(System.getProperty("profilo.html"));
        Document doc = Jsoup.parse(f, StandardCharsets.UTF_8.name(), "https://www.profilo.com");
        ProductImageCrawlerService crawler = new ProductImageCrawlerService(null, null, null, null);
        var groups = crawler.extractSpecGroups(doc);
        System.out.println("== GROUPS: " + groups.size());
        for (var g : groups) {
            System.out.println("--- " + g.title() + " (" + g.items().size() + ")");
            for (var it : g.items()) {
                System.out.println("    " + it.label() + " = " + it.value());
            }
        }
    }
}
