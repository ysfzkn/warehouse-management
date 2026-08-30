package com.warehouse.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Media endpoints that {@code <img>} tags reach without an Authorization header used to
 * be {@code permitAll} on a sequential id — walkable from outside. These cases pin the
 * signature that replaced that.
 */
class SignedUrlServiceTest {

    private final SignedUrlService service = new SignedUrlService("test-secret-for-signing-urls-32chars!!");

    private static String param(String query, String name) {
        for (String pair : query.replaceFirst("^\\?", "").split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    @Test
    void acceptsASignatureItIssued() {
        String query = service.signatureQuery("return-photo", 42L);
        assertThat(service.isValid("return-photo", 42L, param(query, "exp"), param(query, "sig"))).isTrue();
    }

    @Test
    void rejectsAMissingSignature() {
        assertThat(service.isValid("return-photo", 42L, null, null)).isFalse();
        assertThat(service.isValid("return-photo", 42L, "9999999999", null)).isFalse();
    }

    /** Walking ids is the whole attack; one photo's link must not open another's. */
    @Test
    void aSignatureIsBoundToItsId() {
        String query = service.signatureQuery("return-photo", 42L);
        assertThat(service.isValid("return-photo", 43L, param(query, "exp"), param(query, "sig"))).isFalse();
    }

    /** And it must not be reusable against a different endpoint. */
    @Test
    void aSignatureIsBoundToItsResource() {
        String query = service.signatureQuery("return-photo", 42L);
        assertThat(service.isValid("transfer-item-photo", 42L, param(query, "exp"), param(query, "sig"))).isFalse();
    }

    /** Extending the expiry changes the signed payload, so the old signature stops matching. */
    @Test
    void theExpiryCannotBeExtended() {
        String query = service.signatureQuery("return-photo", 42L);
        String farFuture = String.valueOf(Long.parseLong(param(query, "exp")) + 86_400);
        assertThat(service.isValid("return-photo", 42L, farFuture, param(query, "sig"))).isFalse();
    }

    @Test
    void anExpiredSignatureIsRejected() {
        String query = service.signatureQuery("return-photo", 42L);
        assertThat(service.isValid("return-photo", 42L, "1000000000", param(query, "sig"))).isFalse();
    }

    @Test
    void aDifferentKeyProducesADifferentSignature() {
        SignedUrlService other = new SignedUrlService("a-completely-different-secret-value!!");
        String query = service.signatureQuery("return-photo", 42L);
        assertThat(other.isValid("return-photo", 42L, param(query, "exp"), param(query, "sig"))).isFalse();
    }
}
