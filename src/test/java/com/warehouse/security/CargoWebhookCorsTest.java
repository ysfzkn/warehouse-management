package com.warehouse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two things named "cargo webhook" point in opposite directions, and CORS has to tell them apart.
 *
 * <p>{@code /api/public/cargo/**} is Kargonomi calling us, so only carrier origins belong there.
 * {@code /api/admin/cargo/webhook/register} is an administrator's browser calling us to create
 * that registration — an ordinary admin request.
 *
 * <p>The admin path was registered against the carrier config, so the panel's own button came
 * back "Invalid CORS request" while the webhook list rendered beside it worked fine: the list
 * lives at {@code /webhooks}, which never matched the {@code /webhook/**} pattern. These tests
 * pin the direction of each path so the two cannot be merged again.
 */
class CargoWebhookCorsTest {

    private static final String ADMIN_ORIGIN = "https://admin.atsdtm.com.tr";

    private CorsConfigurationSource source() {
        SecurityConfig config = new SecurityConfig(null, null, null, null);
        ReflectionTestUtils.setField(config, "corsAllowedOrigins", ADMIN_ORIGIN);
        return config.corsConfigurationSource();
    }

    private CorsConfiguration configFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Origin", ADMIN_ORIGIN);
        return source().getCorsConfiguration(request);
    }

    @Test
    @DisplayName("Webhook kaydı admin panelinden çağrılabilir")
    void theAdminPanelMayRegisterAWebhook() {
        CorsConfiguration config = configFor("/api/admin/cargo/webhook/register");

        assertThat(config).as("eşleşen CORS yapılandırması yok").isNotNull();
        assertThat(config.checkOrigin(ADMIN_ORIGIN))
                .as("panel origin'i reddedilirse buton 'Invalid CORS request' alır")
                .isEqualTo(ADMIN_ORIGIN);
        assertThat(config.getAllowedHeaders())
                .as("güvenlik şifresi başlığı ön kontrolde geçmeli")
                .contains("X-ADMIN-SECURITY-CODE");
    }

    @Test
    @DisplayName("Webhook listeleme de aynı şekilde çağrılabilir")
    void theAdminPanelMayListWebhooks() {
        assertThat(configFor("/api/admin/cargo/webhooks").checkOrigin(ADMIN_ORIGIN))
                .isEqualTo(ADMIN_ORIGIN);
    }

    /**
     * The same misreading that broke CORS also opened the register endpoint in the URL rules,
     * on the stated premise that it was an HMAC-signed callback. It is not: it verifies an admin
     * security code, and that check returns early for a caller with no admin role. Only the
     * controller's {@code @PreAuthorize} stood between an anonymous POST and the ability to point
     * our shipment notifications — buyer names, phones and addresses — at someone else's server.
     */
    @Test
    @DisplayName("Kayıt ucu URL kurallarında herkese açık bırakılmamalı")
    void theRegisterEndpointIsNotOpenedInTheUrlRules() throws Exception {
        String config = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/warehouse/security/SecurityConfig.java"));

        assertThat(config)
                .as("permitAll yalnızca gerçek callback yolları için olmalı")
                .doesNotContain("\"/api/admin/cargo/webhook/**\").permitAll()")
                .doesNotContain("\"/api/admin/invoice/webhook/**\").permitAll()");
    }

    /**
     * The inbound side must stay closed to browser origins: it is the endpoint that moves orders
     * to "delivered", and its only other guard is the HMAC signature.
     */
    @Test
    @DisplayName("Gelen webhook ucu tarayıcı origin'lerine kapalı kalır")
    void theInboundWebhookStaysClosedToBrowsers() {
        CorsConfiguration config = configFor("/api/public/cargo/kargonomi/webhook");

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin(ADMIN_ORIGIN))
                .as("taşıyıcı ucu panel origin'ini kabul etmemeli")
                .isNull();
        assertThat(config.checkOrigin("https://app.kargonomi.com.tr"))
                .isEqualTo("https://app.kargonomi.com.tr");
    }
}
