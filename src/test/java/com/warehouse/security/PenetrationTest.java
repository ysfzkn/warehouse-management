package com.warehouse.security;

import com.warehouse.repository.SiteSettingRepository;
import com.warehouse.entity.SiteSetting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box penetration tests: real HTTP requests against a running instance, through
 * the complete filter chain, with no mocking of the security layer.
 *
 * <p>Every test here reproduces a concrete finding from the security review. They exist
 * so a rule that quietly opens an endpoint again fails the build rather than production:
 * most of these holes were not written deliberately, they appeared when a new controller
 * landed on the wrong side of a path pattern.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PenetrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private JwtService jwtService;
    @Autowired private SiteSettingRepository siteSettingRepository;

    private static String adminToken;

    /**
     * Logs in once. The admin login bucket allows five attempts per quarter hour, so a
     * per-test login would trip the very rate limiter these tests verify.
     */
    @BeforeAll
    void loginOnce() {
        if (adminToken != null) return;
        ResponseEntity<Map> response = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "admin", "password", "Test1234Secure"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = (String) response.getBody().get("token");
        assertThat(adminToken).isNotBlank();
    }

    // ─────────────────────────── Authentication / authorisation ───────────────────────────

    @Test
    @Order(1)
    void anonymousCannotReachAdminApi() {
        ResponseEntity<String> response = rest.getForEntity("/api/admin/users", String.class);
        assertThat(response.getStatusCode())
                .as("admin API must not answer an unauthenticated caller")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(2)
    void customerTokenCannotReachAdminApi() {
        String customerToken = jwtService.generateCustomerToken(1L, "attacker@example.com");
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/admin/users", customerToken, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * {@code GET /api/admin/users} used to serialise the {@code User} entity directly,
     * handing every administrator's bcrypt hash to anyone holding an admin token.
     */
    @Test
    @Order(3)
    void userListNeverExposesPasswordHashes() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/admin/users", adminToken, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("password hashes must never leave the server")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");
    }

    /**
     * The catch-all chain used to end in {@code permitAll()}, so any controller mounted
     * outside /api/admin and /api/store was world-readable by default — which is how the
     * cargo webhook ended up unauthenticated.
     */
    @Test
    @Order(4)
    void unlistedPathsAreDeniedByDefault() {
        ResponseEntity<String> response = rest.getForEntity("/api/definitely-not-a-real-endpoint", String.class);
        assertThat(response.getStatusCode())
                .as("the public chain must deny by default")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    void actuatorInternalsAreProtectedButHealthIsNot() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/loggers", "/actuator/metrics"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("%s must not be public", path)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @Order(6)
    void apiDocumentationIsNotPublic() {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("%s must not be public", path)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────── Session termination ───────────────────────────

    /**
     * There was no logout endpoint at all: signing out only cleared browser storage, so a
     * token copied beforehand stayed valid for its whole lifetime.
     */
    @Test
    @Order(7)
    void logoutImmediatelyInvalidatesTheToken() {
        ResponseEntity<Map> login = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "admin", "password", "Test1234Secure"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) login.getBody().get("token");

        assertThat(exchange(HttpMethod.GET, "/api/admin/users", token, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.POST, "/api/admin/auth/logout", token, "{}").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.GET, "/api/admin/users", token, null).getStatusCode())
                .as("a revoked token must stop working at once")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    /**
     * The SSE endpoint used to accept the full admin JWT as a query parameter, where it
     * is written to access logs and Referer headers. Only single-use tickets are valid now.
     */
    @Test
    @Order(8)
    void sseRejectsAJwtInTheQueryString() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/admin/stream?token=" + adminToken, String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(9)
    void sseTicketIsSingleUse() {
        ResponseEntity<Map> issued = exchangeForMap(HttpMethod.POST, "/api/admin/stream/ticket", adminToken);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        String ticket = (String) issued.getBody().get("ticket");
        assertThat(ticket).isNotBlank();

        // Redeeming it a second time must fail — the first redemption consumes it.
        // (The first redemption opens a long-lived stream, so it is not exercised here.)
        StreamTicketService.TicketOwner first = streamTicketService.redeem(ticket);
        assertThat(first).isNotNull();
        assertThat(streamTicketService.redeem(ticket)).isNull();
    }

    @Autowired private StreamTicketService streamTicketService;

    // ─────────────────────────── Payment authorisation ───────────────────────────

    /**
     * {@code POST /api/store/payment/initialize} is public (guests pay without an
     * account) and used to act on any order id it was given. It must never succeed
     * without proof of ownership.
     */
    @Test
    @Order(10)
    void paymentInitialisationRefusesAnUnprovenOrder() {
        for (String method : new String[]{"DOOR_CASH", "BANK_TRANSFER", "CREDIT_CARD"}) {
            ResponseEntity<String> response = rest.postForEntity("/api/store/payment/initialize",
                    Map.of("orderId", 1, "paymentMethod", method,
                            "idempotencyKey", "00000000-0000-0000-0000-00000000000" + method.length()),
                    String.class);
            assertThat(response.getStatusCode())
                    .as("payment init for %s must not succeed without an ownership proof", method)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }

    // ─────────────────────────── Webhook authentication ───────────────────────────

    /**
     * The cargo webhook skipped signature verification entirely when no secret was
     * configured, so anyone on the internet could mark orders as delivered.
     *
     * <p>The controller is {@code @Profile("!test")} and so is not mounted here; this
     * asserts only that the path is not silently successful. The signature logic itself
     * is covered by {@code KargonomiWebhookSecurityTest}.</p>
     */
    @Test
    @Order(11)
    void cargoWebhookNeverSucceedsWithoutASignature() {
        ResponseEntity<String> response = rest.postForEntity("/api/public/cargo/kargonomi/webhook",
                new HttpEntity<>("{\"shipment\":{\"id\":\"1\"}}", jsonHeaders(null)), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("an unsigned webhook must never be processed")
                .isFalse();
    }

    // ─────────────────────────── Information disclosure ───────────────────────────

    @Test
    @Order(12)
    void publicSettingsDoNotLeakIntegrationSecretsOrStaffAddresses() {
        siteSettingRepository.save(setting("site_name", "Test Store"));
        siteSettingRepository.save(setting("logo_efatura_endpoint", "https://internal.example.com/soap"));
        siteSettingRepository.save(setting("invoice_admin_digest_email", "boss@example.com"));
        siteSettingRepository.save(setting("logo_company_bank_iban", "TR000000000000000000000000"));
        siteSettingRepository.save(setting("kargonomi_app_key", "super-secret-key"));
        siteSettingRepository.save(setting("seo_local_keywords_extra", "niğde beyaz eşya"));

        ResponseEntity<String> response = rest.getForEntity("/api/store/settings", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();

        assertThat(body).as("ordinary public settings must survive the filter")
                .contains("site_name")
                .contains("seo_local_keywords_extra");
        assertThat(body).as("internal integration details must not be public")
                .doesNotContain("logo_efatura_endpoint")
                .doesNotContain("invoice_admin_digest_email")
                .doesNotContain("logo_company_bank_iban")
                .doesNotContain("kargonomi_app_key")
                .doesNotContain("super-secret-key");
    }

    @Test
    @Order(13)
    void responsesCarryTheHardeningHeaders() {
        HttpHeaders headers = rest.getForEntity("/api/store/settings", String.class).getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isNotBlank();
        assertThat(headers.getFirst("Permissions-Policy")).isNotBlank();
        String csp = headers.getFirst("Content-Security-Policy");
        assertThat(csp).isNotBlank();
        assertThat(csp).contains("frame-ancestors 'none'");
        assertThat(csp).contains("object-src 'none'");
        assertThat(csp).contains("base-uri 'self'");
    }

    /**
     * Return-evidence photos and warehouse delivery photos are reachable without a
     * session (an {@code <img>} tag cannot send a Bearer token), but the sequential id
     * alone used to be enough to walk the whole archive.
     */
    @Test
    @Order(14)
    void mediaEndpointsRequireASignedUrl() {
        for (String path : new String[]{
                "/api/admin/returns/photos/1/view",
                "/api/admin/stock-transfer-items/1/photo/view"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("%s must refuse an unsigned request", path)
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(rest.getForEntity(path + "?exp=9999999999&sig=forged", String.class).getStatusCode())
                    .as("%s must refuse a forged signature", path)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ─────────────────────────── Rate limiting ───────────────────────────

    /**
     * Runs last: it deliberately exhausts a bucket.
     *
     * <p>Each request carries a different {@code X-Forwarded-For}. That used to be a
     * complete bypass — the limiter read the left-most entry, which is whatever the
     * caller typed, so every request landed in a fresh bucket. With the trusted-proxy
     * resolver the header is ignored beyond the configured hop count and all of these
     * still count against the same address.</p>
     */
    @Test
    @Order(20)
    void rateLimitHoldsEvenWithSpoofedForwardedForHeaders() {
        HttpStatus last = null;
        boolean throttled = false;
        for (int i = 0; i < 15; i++) {
            HttpHeaders headers = jsonHeaders(null);
            headers.set("X-Forwarded-For", "203.0.113." + i);
            ResponseEntity<String> response = rest.exchange("/api/store/public/orders/track",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"orderNumber\":\"ORD1\",\"email\":\"a@b.com\"}", headers),
                    String.class);
            last = HttpStatus.valueOf(response.getStatusCode().value());
            if (last == HttpStatus.TOO_MANY_REQUESTS) {
                throttled = true;
                assertThat(response.getHeaders().getFirst("Retry-After")).isNotBlank();
                break;
            }
        }
        assertThat(throttled)
                .as("a spoofed X-Forwarded-For must not buy a fresh rate-limit bucket (last status %s)", last)
                .isTrue();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private SiteSetting setting(String key, String value) {
        SiteSetting existing = siteSettingRepository.findBySettingKey(key).orElseGet(SiteSetting::new);
        existing.setSettingKey(key);
        existing.setSettingValue(value);
        return existing;
    }

    private HttpHeaders jsonHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) headers.setBearerAuth(bearer);
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String bearer, String body) {
        return rest.exchange(path, method, new HttpEntity<>(body, jsonHeaders(bearer)), String.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> exchangeForMap(HttpMethod method, String path, String bearer) {
        return rest.exchange(path, method, new HttpEntity<>("{}", jsonHeaders(bearer)), Map.class);
    }
}
