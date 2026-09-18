package com.warehouse.service.cargo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the carrier answers 401 there is no way to tell, from outside, whether the token is
 * invalid at the carrier or simply stored wrong here. A truncated paste, a quote carried over
 * from a JSON snippet and a stray "Bearer " prefix all produce the same refusal — and all three
 * survive the {@code trim()} the request already applies.
 *
 * <p>The fingerprint has to say enough to settle that in one glance while never printing the
 * credential itself.
 */
class CargoTokenFingerprintTest {

    private static final String REAL = "0htoc1aNkVJhE6mDB0qZhVy69HCIuXzFGM3NOBhu17df3eb6";

    private String describe(String token) {
        return (String) ReflectionTestUtils.invokeMethod(
                CargoReadinessService.class, "describeSecret", token);
    }

    @Test
    @DisplayName("Parmak izi token'ın kendisini yazmaz")
    void theFingerprintNeverPrintsTheToken() {
        String description = describe(REAL);

        assertThat(description).doesNotContain(REAL);
        assertThat(description).contains("48 karakter");
        assertThat(description).contains("0hto").contains("3eb6");
        // The middle is what must never appear.
        assertThat(description).doesNotContain("qZhVy69HCIuXzFGM3NOBhu");
    }

    @Test
    @DisplayName("Kopyalarken bulaşan tırnak ve boşluk söylenir")
    void copyPasteDamageIsNamed() {
        assertThat(describe("  " + REAL + "\n")).contains("boşluk");
        assertThat(describe("\"" + REAL + "\"")).contains("TIRNAK");
        assertThat(describe("Bearer " + REAL)).contains("Bearer");
    }

    @Test
    @DisplayName("Kırpılmış token uzunluğundan belli olur")
    void aTruncatedPasteShowsUpAsLength() {
        assertThat(describe("0hto")).contains("çok kısa");
        assertThat(describe("0htoc1aNkVJhE6")).contains("14 karakter");
    }

    /**
     * An invisible character — a non-breaking space or a zero-width joiner picked up from a web
     * page — is the one cause that leaves the length and both ends looking perfectly correct.
     */
    @Test
    @DisplayName("Görünmez karakter de yakalanır")
    void anInvisibleCharacterIsCaught() {
        String withNbsp = "0htoc1aNkVJhE6mDB0qZhVy69HCIuXzFGM3NOBhu17df3eb ";

        assertThat(describe(withNbsp)).contains("ASCII dışı");
        assertThat(describe(REAL)).doesNotContain("ASCII dışı");
    }
}
