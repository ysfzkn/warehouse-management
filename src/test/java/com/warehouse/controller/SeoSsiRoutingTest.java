package com.warehouse.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * nginx splices the backend's SEO fragments into index.html (SSI). Each directive below
 * guards a failure that is silent — the page still loads, only what crawlers see is wrong:
 *
 * <ul>
 *   <li>without {@code ssi on} the include comments reach the browser verbatim and every
 *       page keeps the one static title;</li>
 *   <li>without error interception a backend error body (JSON) is pasted into the head;</li>
 *   <li>with the client's Accept-Encoding the backend may gzip, and binary lands in the HTML;</li>
 *   <li>with the decoded subrequest path instead of {@code $request_uri}, slugs with Turkish
 *       letters reach Tomcat as raw UTF-8 and get 400 — those pages quietly lose their title;</li>
 *   <li>without {@code internal} anyone could fetch the fragments through the storefront.</li>
 * </ul>
 *
 * <p>The routing lives outside the application; reading the config is the only way to hold
 * it. It was verified against a real nginx before this test was written.</p>
 */
class SeoSsiRoutingTest {

    private static final Path NGINX = Path.of("frontend/nginx.conf");
    private static final Pattern SEO_LOCATION =
            Pattern.compile("location ~ \\^/__seo/[^{]*\\{(.*?)\\n    }", Pattern.DOTALL);
    private static final Pattern SPA_LOCATION =
            Pattern.compile("location / \\{(.*?)\\n    }", Pattern.DOTALL);

    private static String block(Pattern pattern) throws IOException {
        Matcher m = pattern.matcher(Files.readString(NGINX));
        assertThat(m.find()).as("%s içinde %s bloğu yok", NGINX, pattern.pattern()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("SPA konumunda SSI açık ve hatalar sessiz")
    void theSpaLocationProcessesIncludes() throws IOException {
        assertThat(block(SPA_LOCATION)).contains("ssi on;").contains("ssi_silent_errors on;");
    }

    @Test
    @DisplayName("SEO parçası: dahili, hatalar boş gövdeye, gzip kapalı, kodlanmış URI")
    void theFragmentLocationFailsEmptyAndPassesTheEncodedUri() throws IOException {
        String seo = block(SEO_LOCATION);

        assertThat(seo).as("dışarıdan çağrılabilir olmamalı").contains("internal;");
        assertThat(seo).as("hata gövdesi head'e yapışır").contains("proxy_intercept_errors on;");
        assertThat(seo).as("gzip gövde HTML'e ikili veri olarak girer").contains("proxy_set_header Accept-Encoding \"\";");
        assertThat(seo).as("Türkçe harfli slug Tomcat'e ham UTF-8 gider, 400 alır").contains("$request_uri;");
    }

    @Test
    @DisplayName("Docker derlemesi SSI eklemesini çalıştırır")
    void theImageBuildInjectsTheIncludes() throws IOException {
        assertThat(Files.readString(Path.of("frontend/Dockerfile")))
                .as("betik çalışmazsa include'lar hiç eklenmez ve kimse fark etmez")
                .contains("node scripts/inject-ssi.js build/index.html");
    }
}
