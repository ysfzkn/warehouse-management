package com.warehouse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one rule four call sites used to each have their own version of.
 *
 * <p>What holds it together: a prefix is stripped only when what remains is the right length.
 * That is what stops a number carrying an extension from being silently trimmed into a
 * well-formed wrong number — which is worse than a rejected one, because it would be dialled.
 */
class TurkishPhoneTest {

    @ParameterizedTest
    @CsvSource({
            "5321112233,       5321112233",
            "05321112233,      5321112233",
            "+905321112233,    5321112233",
            "00905321112233,   5321112233",
            "'0532 111 22 33', 5321112233",
            "'(0532) 111-2233',5321112233",
    })
    @DisplayName("Her yazım biçimi aynı on haneye iner")
    void everyWrittenFormReducesToTheSameTenDigits(String written, String expected) {
        assertThat(TurkishPhone.national(written)).isEqualTo(expected);
        assertThat(TurkishPhone.isValid(written)).isTrue();
    }

    /**
     * The failure that sent us here: Kargonomi answered "Gönderici Telefon 1 (Mobil) 10 rakam
     * olmalıdır" for a number that every "is it filled in" check had passed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "532111223",                    // a digit short
            "0532 111 22 33 / 0388 232 45 67",  // two numbers in one field
            "0532 111 22 33 dahili 12",     // with an extension
            "",
    })
    @DisplayName("On haneye inmeyen numara geçersiz sayılır, kırpılmaz")
    void anythingThatDoesNotReduceToTenDigitsIsRefusedRatherThanTrimmed(String written) {
        assertThat(TurkishPhone.isValid(written)).isFalse();
    }

    @Test
    @DisplayName("Sabit hat geçerli ama mobil değil")
    void aLandlineIsValidButNotMobile() {
        assertThat(TurkishPhone.isValid("0388 232 45 67")).isTrue();
        assertThat(TurkishPhone.isMobile("0388 232 45 67")).isFalse();
        assertThat(TurkishPhone.isMobile("0532 111 22 33")).isTrue();
    }

    @Test
    @DisplayName("Yurt içi yazım biçimi baştaki sıfırı geri koyar")
    void theDomesticFormPutsTheTrunkZeroBack() {
        assertThat(TurkishPhone.withTrunkZero("+90 532 111 22 33")).isEqualTo("05321112233");
    }

    @Test
    @DisplayName("null girdi null döner")
    void nullSurvives() {
        assertThat(TurkishPhone.national(null)).isNull();
        assertThat(TurkishPhone.isValid(null)).isFalse();
    }
}
