package com.warehouse.controller;

import com.warehouse.repository.OrderRepository;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.cargo.CargoApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The cargo webhook sits under {@code /api/public/**} and is reachable by anyone on the
 * internet; its HMAC signature is the only thing separating the carrier from an attacker.
 *
 * <p>It used to skip verification entirely when no secret was configured, logging a
 * warning and processing the payload anyway — so an unauthenticated POST could move any
 * order to "delivered". These tests pin the fail-closed behaviour.</p>
 *
 * <p>Direct unit test rather than an HTTP one because the controller is annotated
 * {@code @Profile("!test")} and is therefore not registered in the test context.</p>
 */
@ExtendWith(MockitoExtension.class)
class KargonomiWebhookSecurityTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CargoApiService cargoApiService;
    @Mock private SiteSettingService settingService;

    private KargonomiWebhookController controller;

    private static final String BODY = "{\"shipment\":{\"id\":\"SHIP-1\",\"tracking_code\":\"TRK-1\"}}";

    @BeforeEach
    void setUp() {
        controller = new KargonomiWebhookController(orderRepository, cargoApiService, settingService);
    }

    @Test
    void refusesEverythingWhileNoSecretIsConfigured() {
        when(settingService.getSetting("kargonomi_webhook_secret")).thenReturn("");

        ResponseEntity<Map<String, Object>> response = controller.receive("any-signature", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(orderRepository, cargoApiService);
    }

    @Test
    void refusesAMissingSignature() {
        when(settingService.getSetting("kargonomi_webhook_secret")).thenReturn("s3cret");

        ResponseEntity<Map<String, Object>> response = controller.receive(null, BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(orderRepository, cargoApiService);
    }

    @Test
    void refusesAForgedSignature() {
        when(settingService.getSetting("kargonomi_webhook_secret")).thenReturn("s3cret");

        ResponseEntity<Map<String, Object>> response = controller.receive("deadbeef", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(orderRepository, cargoApiService);
    }

    @Test
    void acceptsACorrectlySignedPayload() {
        when(settingService.getSetting("kargonomi_webhook_secret")).thenReturn("s3cret");
        when(orderRepository.findByCargoProviderShipmentId("SHIP-1")).thenReturn(java.util.Optional.empty());
        when(orderRepository.findByCargoTrackingNo("TRK-1")).thenReturn(java.util.Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.receive(hmacSha256Hex("s3cret", BODY), BODY);

        // No matching order in this fixture, but the signature passed — which is the
        // point: a valid signature gets past the gate, a forged one does not.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
