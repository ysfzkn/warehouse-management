package com.warehouse.service.cargo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failed webhook registration has to arrive with its reason attached.
 *
 * <p>It used to come back as a bare {@code false}: the panel showed {@code {"success": false}}
 * and the cause — a refused token, a rejected URL, a carrier that never answered — stayed in a
 * server log line that an administrator cannot read. Each of those needs a different action, so
 * the three must not collapse into one another.
 */
class CargoWebhookRegistrationTest {

    @Test
    @DisplayName("Başarısız kayıt sebebini taşır")
    void aFailureCarriesItsReason() {
        CargoWebhookRegistration failed =
                CargoWebhookRegistration.failed("Kargonomi API token'ını reddetti (HTTP 401).");

        assertThat(failed.success()).isFalse();
        assertThat(failed.webhook()).isNull();
        assertThat(failed.reason()).contains("401");
        assertThat(failed.issuedSecret())
                .as("kayıt olmadıysa anahtar da yoktur")
                .isNull();
    }

    @Test
    @DisplayName("Kargonomi anahtarı üretirse yakalanır")
    void anIssuedKeyIsPickedUpWhicheverNameItArrivesUnder() {
        assertThat(CargoWebhookRegistration.ok(Map.of("id", 7, "secret", "abc")).issuedSecret())
                .isEqualTo("abc");
        assertThat(CargoWebhookRegistration.ok(Map.of("secret_key", "def")).issuedSecret())
                .isEqualTo("def");
        assertThat(CargoWebhookRegistration.ok(Map.of("signature_key", "ghi")).issuedSecret())
                .isEqualTo("ghi");
    }

    /**
     * Null here means "the key comes from somewhere else", not "the registration failed" — the
     * two were worth keeping apart, because only one of them calls for retrying.
     */
    @Test
    @DisplayName("Anahtar dönmemesi başarısızlık değildir")
    void aMissingKeyIsStillASuccess() {
        CargoWebhookRegistration created = CargoWebhookRegistration.ok(Map.of("id", 7));

        assertThat(created.success()).isTrue();
        assertThat(created.issuedSecret()).isNull();
        assertThat(created.reason()).isNull();
    }
}
