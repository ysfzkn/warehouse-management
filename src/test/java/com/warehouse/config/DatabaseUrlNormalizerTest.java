package com.warehouse.config;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bu sınıfın var oluş sebebi üretimde yaşandı: Railway'in ürettiği bağlantı dizesi JDBC
 * sürücüsünün beklediği biçimde değil ve elle çevrilmesi gereken her deploy'da yanlış
 * yapıldı. Testler o somut hataları sabitliyor — biçim dönüşümünü ve kimlik bilgisinin
 * URL'den ayıklanmasını.
 */
class DatabaseUrlNormalizerTest {

    private static final DeferredLogFactory LOG_FACTORY =
            destination -> LogFactory.getLog(DatabaseUrlNormalizerTest.class);

    @Test
    @DisplayName("Railway'in ürettiği postgresql:// dizesi JDBC biçimine çevrilir")
    void convertsPlatformUrlToJdbc() {
        var result = DatabaseUrlNormalizer.normalize(
                "postgresql://postgres:s3cret@postgres-xojd.railway.internal:5432/railway");

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo("jdbc:postgresql://postgres-xojd.railway.internal:5432/railway");
        assertThat(result.get().username()).isEqualTo("postgres");
        assertThat(result.get().password()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("jdbc: önekli ama kimlik bilgisi gömülü URL host sanılmaz, ayrıştırılır")
    void extractsCredentialsFromJdbcPrefixedUrl() {
        var result = DatabaseUrlNormalizer.normalize(
                "jdbc:postgresql://postgres:s3cret@db.railway.internal:5432/railway");

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo("jdbc:postgresql://db.railway.internal:5432/railway");
        assertThat(result.get().username()).isEqualTo("postgres");
        assertThat(result.get().password()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("Zaten doğru olan URL'e dokunulmaz")
    void leavesCorrectJdbcUrlAlone() {
        String url = "jdbc:postgresql://db.railway.internal:5432/railway";

        var result = DatabaseUrlNormalizer.normalize(url);

        assertThat(result).isPresent();
        assertThat(result.get().isSameAs(url)).isTrue();
    }

    @Test
    @DisplayName("Port belirtilmemişse 5432 varsayılır")
    void defaultsMissingPort() {
        var result = DatabaseUrlNormalizer.normalize("postgresql://db.railway.internal/railway");

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo("jdbc:postgresql://db.railway.internal:5432/railway");
        assertThat(result.get().username()).isNull();
    }

    @Test
    @DisplayName("Sorgu parametreleri korunur")
    void keepsQueryParameters() {
        var result = DatabaseUrlNormalizer.normalize(
                "postgresql://db.railway.internal:5432/railway?sslmode=require&ApplicationName=warehouse");

        assertThat(result).isPresent();
        assertThat(result.get().url())
                .isEqualTo("jdbc:postgresql://db.railway.internal:5432/railway?sslmode=require&ApplicationName=warehouse");
    }

    @Test
    @DisplayName("Yüzde kodlu parola çözülür, artı işareti boşluğa dönmez")
    void decodesPasswordWithoutCorruptingPlusSign() {
        var result = DatabaseUrlNormalizer.normalize(
                "postgresql://postgres:a%40b%2Fc+d@db.railway.internal:5432/railway");

        assertThat(result).isPresent();
        assertThat(result.get().password()).isEqualTo("a@b/c+d");
    }

    @ParameterizedTest
    @DisplayName("Postgres olmayan ya da ayrıştırılamayan değerlere dokunulmaz")
    @ValueSource(strings = {
            "jdbc:h2:mem:test",
            "jdbc:mysql://db:3306/warehouse",
            "postgresql://",
            "bu bir url değil"
    })
    void ignoresUnrelatedUrls(String url) {
        assertThat(DatabaseUrlNormalizer.normalize(url)).isEmpty();
    }

    @Test
    @DisplayName("URL'deki kimlik bilgisi, elle girilmiş yanlış parolayı ezer")
    void urlCredentialsOverrideSeparatelyConfiguredOnes() {
        var environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url",
                "postgresql://postgres:dogru@db.railway.internal:5432/railway");
        environment.setProperty("spring.datasource.username", "yanlis");
        environment.setProperty("spring.datasource.password", "yanlis");

        new DatabaseUrlNormalizer(LOG_FACTORY).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.railway.internal:5432/railway");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("postgres");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("dogru");
    }

    @Test
    @DisplayName("Çözülemeyen yer tutucu uygulamayı düşürmez")
    void survivesUnresolvablePlaceholder() {
        var environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "${DATABASE_URL}");

        new DatabaseUrlNormalizer(LOG_FACTORY).postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains("normalizedDatasource")).isFalse();
    }
}
