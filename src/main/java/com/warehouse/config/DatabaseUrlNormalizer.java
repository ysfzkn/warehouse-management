package com.warehouse.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Neden: Railway ve Heroku türevi platformlar Postgres bağlantısını
 * {@code postgresql://kullanici:parola@host:5432/db} biçiminde üretir. JDBC sürücüsü bu şemayı
 * tanımaz ("claims to not accept jdbcUrl"); kullanıcı bilgisi {@code jdbc:} önekli bir URL'e
 * gömülürse de {@code kullanici:parola@host} tek parça host sanılıp UnknownHostException'a
 * dönüşür. Dönüşümü elle yapmak, veritabanı servisi her değiştiğinde tekrarlanan ve sessizce
 * yanlış yapılabilen bir adım olduğu için burada, uygulama açılırken bir kez yapıyoruz.
 *
 * <p>URL'de kullanıcı bilgisi varsa kimlik bilgileri oradan alınır ve ayrı ayrı verilen
 * {@code spring.datasource.username} / {@code password} değerlerini ezer: ikisi çeliştiğinde
 * URL ile birlikte gelen çift, elle kopyalanmış olandan daha güvenilirdir.
 */
public class DatabaseUrlNormalizer implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "normalizedDatasource";
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String USERNAME_PROPERTY = "spring.datasource.username";
    private static final String PASSWORD_PROPERTY = "spring.datasource.password";

    private static final String JDBC_PREFIX = "jdbc:";
    private static final String JDBC_POSTGRES_PREFIX = "jdbc:postgresql://";
    private static final Set<String> POSTGRES_SCHEMES = Set.of("postgresql", "postgres");
    private static final int DEFAULT_PORT = 5432;

    private final Log log;

    public DatabaseUrlNormalizer(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseUrlNormalizer.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = readConfiguredUrl(environment);
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        Optional<Datasource> normalized = normalize(rawUrl);
        if (normalized.isEmpty()) {
            if (!rawUrl.trim().startsWith(JDBC_POSTGRES_PREFIX)) {
                log.warn("spring.datasource.url tanınmadı, olduğu gibi bırakılıyor. Beklenen biçim: "
                        + "jdbc:postgresql://host:5432/db ya da postgresql://kullanici:parola@host:5432/db");
            }
            return;
        }

        Datasource datasource = normalized.get();
        if (datasource.isSameAs(rawUrl.trim())) {
            return;
        }

        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, datasource.toProperties()));
        log.info("Veritabanı URL'i JDBC biçimine çevrildi: " + datasource.describe());
    }

    /**
     * Neden sarmalanmış: {@code spring.datasource.url} çoğu profilde {@code ${DATABASE_URL}}
     * yer tutucusudur. Değişken tanımlı değilse çözümleme istisna atar; bu noktada uygulamayı
     * düşürmek yerine dokunmadan geçiyoruz ki asıl hatayı DataSource kurulumu anlaşılır biçimde
     * bildirsin.
     */
    private String readConfiguredUrl(ConfigurableEnvironment environment) {
        try {
            return environment.getProperty(URL_PROPERTY);
        } catch (IllegalArgumentException e) {
            log.debug("spring.datasource.url çözümlenemedi, normalleştirme atlanıyor: " + e.getMessage());
            return null;
        }
    }

    static Optional<Datasource> normalize(String rawUrl) {
        String candidate = rawUrl.trim();
        if (candidate.startsWith(JDBC_PREFIX)) {
            candidate = candidate.substring(JDBC_PREFIX.length());
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !POSTGRES_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                || host == null || host.isBlank()) {
            return Optional.empty();
        }

        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();
        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String url = JDBC_POSTGRES_PREFIX + host + ":" + port + path + query;

        return Optional.of(withCredentials(url, uri.getRawUserInfo()));
    }

    private static Datasource withCredentials(String url, String rawUserInfo) {
        if (rawUserInfo == null || rawUserInfo.isBlank()) {
            return new Datasource(url, null, null);
        }
        int separator = rawUserInfo.indexOf(':');
        if (separator < 0) {
            return new Datasource(url, decode(rawUserInfo), null);
        }
        return new Datasource(url,
                decode(rawUserInfo.substring(0, separator)),
                decode(rawUserInfo.substring(separator + 1)));
    }

    /**
     * Neden {@code +} önce kaçırılıyor: {@link URLDecoder} onu boşluğa çevirir, oysa URL'in
     * kullanıcı bilgisi bölümünde artı işareti gerçek bir karakterdir. İçinde artı geçen bir
     * parola aksi hâlde sessizce bozulur.
     */
    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        // Yapılandırma dosyaları yüklendikten sonra çalışmalı, yoksa okunacak bir URL olmaz.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    record Datasource(String url, String username, String password) {

        boolean isSameAs(String rawUrl) {
            return username == null && password == null && url.equals(rawUrl);
        }

        Map<String, Object> toProperties() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put(URL_PROPERTY, url);
            if (username != null) {
                properties.put(USERNAME_PROPERTY, username);
            }
            if (password != null) {
                properties.put(PASSWORD_PROPERTY, password);
            }
            return properties;
        }

        /** Parola asla log'a yazılmaz; bağlantı hatalarında URL'in tamamı log'a düşüyordu. */
        String describe() {
            return url + (username == null ? " (kimlik bilgisi URL'de yok)" : " (kullanıcı: " + username + ")");
        }
    }
}
