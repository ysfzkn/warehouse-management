package com.warehouse.service.seo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The city is an admin setting, so the locative suffix cannot be hard-coded as "'de":
 * the same descriptions would read "Ankara'de" for a store in Ankara. These cases pin the
 * vowel-harmony and consonant-hardening rules; seo.js implements the same ones.
 */
class LocalSeoTextTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "Niğde, Niğde'de",
            "Ankara, Ankara'da",
            "Kayseri, Kayseri'de",
            "Aksaray, Aksaray'da",
            "Sivas, Sivas'ta",
            "Nevşehir, Nevşehir'de",
            "Konya, Konya'da",
            "Bursa, Bursa'da",
            "Tokat, Tokat'ta",
            "Kütahya, Kütahya'da",
            "Ünye, Ünye'de",
    })
    @DisplayName("Bulunma eki ünlü uyumuna ve sertleşmeye uyar")
    void locativeFollowsVowelHarmonyAndHardening(String city, String expected) {
        assertThat(LocalSeoText.locative(city)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Şehir zaten metindeyse ikinci kez eklenmez")
    void cityIsNotRepeated() {
        assertThat(LocalSeoText.withCity("Niğde", "NİĞDE Profilo Buzdolabı")).isEqualTo("NİĞDE Profilo Buzdolabı");
        assertThat(LocalSeoText.withCity("Niğde", "Profilo Buzdolabı")).isEqualTo("Niğde Profilo Buzdolabı");
        assertThat(LocalSeoText.withCity("", "Profilo")).isEqualTo("Profilo");
    }

    @Test
    @DisplayName("Marka adda zaten geçiyorsa başlığa tekrar eklenmez")
    void brandIsNotRepeated() {
        assertThat(LocalSeoText.withBrand("Regal 140 Lt Büro Tipi Buzdolabı", "Regal"))
                .isEqualTo("Regal 140 Lt Büro Tipi Buzdolabı");
        assertThat(LocalSeoText.withBrand("SIMFER 8738 Davlumbaz", "Simfer")).isEqualTo("SIMFER 8738 Davlumbaz");
        assertThat(LocalSeoText.withBrand("11 DLM Yağlı Radyatör", "Altus")).isEqualTo("11 DLM Yağlı Radyatör Altus");
        assertThat(LocalSeoText.withBrand("Tost Makinesi", null)).isEqualTo("Tost Makinesi");
    }

    @Test
    @DisplayName("Zengin metin düz metne iner, kelime ortasından kesilmez")
    void richTextIsFlattenedAndCutAtAWord() {
        String text = LocalSeoText.plainText("<p>Enerji <b>sınıfı</b> A+++ olan geniş hacimli buzdolabı</p>", 30);

        assertThat(text).isEqualTo("Enerji sınıfı A+++ olan geniş…");
    }
}
