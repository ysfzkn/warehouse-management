package com.warehouse.service.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Manual smoke test against locally saved product pages
 * (-Dprofilo.html=C:\path\a.html;C:\path\b.html — semicolon-separated).
 * Prints grouped + flat spec counts and image count per file.
 * Skipped unless the property is set, so CI never depends on external HTML.
 */
class ProfiloLivePageManualTest {

    @Test
    @EnabledIfSystemProperty(named = "profilo.html", matches = ".+")
    void printGroupsFromSavedPages() throws Exception {
        ProductImageCrawlerService crawler = new ProductImageCrawlerService(null, null, null, null);
        for (String path : System.getProperty("profilo.html").split(";")) {
            if (path.isBlank()) continue;
            File f = new File(path.trim());
            System.out.println("#### FILE: " + f.getName());
            if (!f.exists()) {
                System.out.println("  (missing)");
                continue;
            }
            Document doc = Jsoup.parse(f, StandardCharsets.UTF_8.name(), "https://example.com");
            var groups = crawler.extractSpecGroups(doc);
            var flat = crawler.extractSpecs(doc);
            System.out.println("== GROUPS: " + groups.size() + "  FLAT: " + flat.size());
            for (var g : groups) {
                System.out.println("--- " + g.title() + " (" + g.items().size() + ")");
                for (var it : g.items()) {
                    System.out.println("    " + it.label() + " = " + it.value());
                }
            }
            if (groups.isEmpty() && !flat.isEmpty()) {
                flat.forEach((k, v) -> System.out.println("    [flat] " + k + " = " + v));
            }
        }
    }
}
