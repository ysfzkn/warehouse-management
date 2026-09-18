package com.warehouse.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Standards that are cheap to state and expensive to rediscover, held in place mechanically.
 *
 * <p>Each rule here has already cost real debugging time. A convention nobody checks decays back
 * to whatever the last person typed, and these particular failures are silent: a mistyped setting
 * key reads as an unconfigured feature, and a locale-less case conversion quietly stops matching
 * Turkish names. Neither fails to compile and neither throws.
 */
class CodingStandardsTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    private List<String> scanSources(SourceRule rule) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file);
                String name = file.getFileName().toString();
                rule.check(name, content, violations);
            }
        }
        return violations;
    }

    @FunctionalInterface
    private interface SourceRule {
        void check(String fileName, String content, List<String> violations);
    }

    /**
     * {@code new Locale(String, String)} is deprecated as of Java 19, and every call site was
     * building the same Turkish locale by hand. One constant, one supported constructor.
     */
    @Test
    @DisplayName("Kullanımdan kaldırılmış Locale kurucusu kalmamalı")
    void noDeprecatedLocaleConstructorRemains() throws IOException {
        List<String> violations = scanSources((name, content, out) -> {
            // Locales is where the rule is written down, so its Javadoc names the thing it bans.
            if (name.equals("Locales.java")) return;
            if (content.contains("new Locale(")) {
                out.add(name + " — new Locale(...) yerine Locales.TR kullanın");
            }
        });

        assertThat(violations).isEmpty();
    }

    /**
     * A key typed by hand does not fail to compile and does not throw — {@code getSetting} simply
     * returns nothing, so the feature behaves exactly as though it were never configured.
     */
    @Test
    @DisplayName("Ayar anahtarları sabitten gelmeli, elle yazılmamalı")
    void settingKeysComeFromConstants() throws IOException {
        List<String> violations = scanSources((name, content, out) -> {
            if (name.equals("SettingKeys.java")) return;
            if (content.contains("getSetting(\"") || content.contains("setSetting(\"")) {
                out.add(name + " — ayar anahtarını SettingKeys sabitinden alın");
            }
        });

        assertThat(violations).isEmpty();
    }

    /**
     * Logging goes through slf4j so it carries a level, a logger name and a timestamp. A bare
     * print reaches the container log with none of those and cannot be filtered or silenced.
     */
    @Test
    @DisplayName("Üretim kodunda System.out/err kullanılmamalı")
    void productionCodeLogsThroughSlf4j() throws IOException {
        List<String> violations = scanSources((name, content, out) -> {
            if (content.contains("System.out.print") || content.contains("System.err.print")) {
                out.add(name + " — logger kullanın");
            }
        });

        assertThat(violations).isEmpty();
    }
}
