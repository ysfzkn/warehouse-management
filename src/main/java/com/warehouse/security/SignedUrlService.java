package com.warehouse.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Expiring, tamper-proof links for media that cannot carry an Authorization header.
 *
 * <p>Some images are rendered with a plain {@code <img src>}, which cannot send the
 * Bearer token the admin panel authenticates with. The workaround was to mark those
 * endpoints {@code permitAll} — but the identifiers are sequential database ids, so
 * anyone could walk {@code /api/admin/returns/photos/1..N/view} and collect customers'
 * return-evidence photos, or the equivalent stock-transfer photo view for the
 * warehouse's internal delivery photos.</p>
 *
 * <p>The endpoints stay reachable without a session, but only with a signature the
 * server produced for that exact resource, and only until it expires. Nothing changes
 * for the frontend: it already renders whatever URL the API hands it.</p>
 */
@Service
public class SignedUrlService {

    /** Long enough to browse a page and open a lightbox, short enough to be useless if copied out. */
    private static final Duration DEFAULT_TTL = Duration.ofHours(6);

    /**
     * Static handle for callers Spring cannot inject into — {@code ReturnDtoMapper} is a
     * static utility used from several layers. It is still an ordinary singleton bean;
     * this only exposes it where constructor injection is not available.
     */
    private static volatile SignedUrlService instance;

    private final SecretKeySpec key;

    public SignedUrlService(@Value("${app.security.jwt-secret:}") String jwtSecret) {
        // Domain-separated from the JWT signing use of the same secret, so a signature
        // from one context can never be replayed in the other.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("warehouse:signed-url:v1".getBytes(StandardCharsets.UTF_8));
            digest.update((jwtSecret == null ? "" : jwtSecret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest.digest(), "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("İmzalama anahtarı türetilemedi", e);
        }
        instance = this;
    }

    /** Static convenience for the signing side. */
    public static String query(String resource, Object id) {
        SignedUrlService service = instance;
        // Before the context is up there is nothing to sign with; an unsigned URL simply
        // fails verification, which is the safe direction.
        return service == null ? "" : service.signatureQuery(resource, id);
    }

    /** Static convenience for the verifying side. */
    public static boolean valid(String resource, Object id, String exp, String sig) {
        SignedUrlService service = instance;
        return service != null && service.isValid(resource, id, exp, sig);
    }

    /**
     * Builds the query suffix for a resource, including the leading {@code ?}.
     *
     * @param resource stable label for the endpoint, e.g. {@code "return-photo"}
     * @param id       the identifier that appears in the path
     */
    public String signatureQuery(String resource, Object id) {
        long expiresAt = Instant.now().plus(DEFAULT_TTL).getEpochSecond();
        return "?exp=" + expiresAt + "&sig=" + sign(resource, String.valueOf(id), expiresAt);
    }

    /** True when the signature matches this resource and has not expired. */
    public boolean isValid(String resource, Object id, String exp, String sig) {
        if (exp == null || sig == null) return false;
        long expiresAt;
        try {
            expiresAt = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Instant.now().getEpochSecond() > expiresAt) return false;
        String expected = sign(resource, String.valueOf(id), expiresAt);
        // Constant time: a byte-by-byte comparison would leak the signature under
        // repeated requests.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String resource, String id, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String payload = resource + "|" + id + "|" + expiresAt;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("URL imzalanamadı", e);
        }
    }
}
