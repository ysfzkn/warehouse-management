package com.warehouse.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reverse proxy must hand {@code /api/public/**} to the backend untouched.
 *
 * <p>Both nginx configs carry a backwards-compatibility rule that prefixes any {@code /api/X}
 * with {@code /api/admin/}, for SPA code that calls {@code /api/stocks} and similar. Its
 * exclusion list named admin, store and info but not public, so Kargonomi's callback to
 * {@code /api/public/cargo/kargonomi/webhook} reached the backend as
 * {@code /api/admin/public/cargo/kargonomi/webhook}. The admin chain refuses that, the carrier's
 * reachability check saw 403, and the webhook could not be registered at all.
 *
 * <p>Excluding the path is only half of it: with no location block of its own, the request
 * matches nothing and is served the SPA's index.html with a 200 — a webhook that looks delivered
 * and is silently dropped, which is the worse of the two failures because nothing reports it.
 *
 * <p>No Java test could have caught this; the routing lives outside the application. Reading the
 * config files is the only way to hold it still.
 */
class PublicApiRoutingTest {

    private static final List<Path> NGINX_CONFIGS = List.of(
            Path.of("nginx/prod.conf"),
            Path.of("frontend/nginx.conf"));

    /** The backwards-compatibility rewrite and the paths it must leave alone. */
    private static final Pattern ADMIN_REWRITE_RULE =
            Pattern.compile("location\\s+~\\s+\\^/api/\\(\\?!([^)]+)\\)");

    @Test
    @DisplayName("/api/public admin önekiyle yeniden yazılmamalı")
    void thePublicPrefixIsExemptFromTheAdminRewrite() throws IOException {
        for (Path config : NGINX_CONFIGS) {
            String text = Files.readString(config);
            var matcher = ADMIN_REWRITE_RULE.matcher(text);

            assertThat(matcher.find())
                    .as("%s içinde admin rewrite kuralı bulunamadı", config)
                    .isTrue();
            assertThat(matcher.group(1))
                    .as("%s: public/ muaf değilse gelen webhook /api/admin/public/... olur", config)
                    .contains("public/");
        }
    }

    @Test
    @DisplayName("/api/public backend'e yönlendiren kendi bloğuna sahip olmalı")
    void thePublicPrefixIsProxiedRatherThanFallingThroughToTheSpa() throws IOException {
        for (Path config : NGINX_CONFIGS) {
            assertThat(Files.readString(config))
                    .as("%s: blok yoksa istek index.html alır ve webhook sessizce kaybolur", config)
                    .contains("location /api/public/");
        }
    }
}
