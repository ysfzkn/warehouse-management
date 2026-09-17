package com.warehouse.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two migrations must never share a version number.
 *
 * <p>Flyway refuses to start at all when it finds a duplicate — it fails while scanning, before
 * applying anything — so the application never boots and the deployment dies on its health check
 * with no clue as to why. That is exactly what happened when a cargo migration was numbered
 * {@code V100} while {@code V100__receipt_carrier_backfill.sql} already existed on main.
 *
 * <p>Nothing caught it: the test profile sets {@code spring.flyway.enabled=false} and builds the
 * schema from the entities instead, so the whole suite stayed green while the deployable artifact
 * could not start. This test reads the migration directory directly, which is the one way to see
 * the collision without a database.
 */
class FlywayMigrationVersionTest {

    private static final Path MIGRATION_DIR = Paths.get("src/main/resources/db/migration");

    /** Flyway's own naming rule: V, a version, two underscores, a description, then {@code .sql}. */
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.+\\.sql$");

    @Test
    @DisplayName("Aynı sürüm numarasını iki migration paylaşamaz")
    void noTwoMigrationsShareAVersion() throws IOException {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            files.map(path -> path.getFileName().toString())
                    .forEach(name -> {
                        Matcher matcher = VERSIONED_MIGRATION.matcher(name);
                        if (matcher.matches()) {
                            byVersion.computeIfAbsent(matcher.group(1), v -> new ArrayList<>()).add(name);
                        }
                    });
        }

        List<String> collisions = new ArrayList<>();
        byVersion.forEach((version, names) -> {
            if (names.size() > 1) {
                collisions.add("V" + version + " → " + String.join(", ", names));
            }
        });

        assertThat(collisions)
                .as("Flyway çakışan sürümde hiç başlamaz; uygulama ayağa kalkmaz. "
                        + "Çakışan dosyalardan yenisini en yüksek sürümün üstüne taşı.")
                .isEmpty();
    }

    @Test
    @DisplayName("Migration klasöründe Flyway'in okuyamayacağı dosya bulunmamalı")
    void everySqlFileFollowsTheNamingRule() throws IOException {
        List<String> misnamed = new ArrayList<>();

        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    // Repeatable migrations (R__) are legitimate and carry no version.
                    .filter(name -> !name.startsWith("R__"))
                    .filter(name -> !VERSIONED_MIGRATION.matcher(name).matches())
                    .forEach(misnamed::add);
        }

        assertThat(misnamed)
                .as("Adı kurala uymayan .sql dosyası sessizce atlanır — migration hiç çalışmaz")
                .isEmpty();
    }
}
