package com.warehouse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The crawler fetches URLs an operator pastes in, which makes the backend a potential
 * proxy into its own network. These cases pin the gaps the original inline check had.
 */
class SsrfGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/api/admin/users",
            "http://localhost/actuator/env",
            // Cloud metadata — the classic SSRF target.
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            "http://10.0.0.5/internal",
            "http://192.168.1.1/",
            "http://172.16.0.1/",
            // Carrier-grade NAT: the old check only covered the 100.64.x /24.
            "http://100.100.0.1/",
            "http://100.127.255.254/",
            // IPv4-mapped IPv6 — previously not unwrapped.
            "http://[::ffff:127.0.0.1]/",
            // Unique-local IPv6 — isSiteLocalAddress() does not recognise fc00::/7.
            "http://[fd00::1]/",
            "http://[::1]/",
            "http://0.0.0.0/",
    })
    void blocksInternalDestinations(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "gopher://127.0.0.1:11211/",
            "jar:http://example.com!/",
            "ftp://example.com/x",
    })
    void blocksNonHttpSchemes(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }

    @Test
    void allowsOrdinaryPublicHosts() {
        assertThat(SsrfGuard.validate("https://example.com/product/1").getHost())
                .isEqualTo("example.com");
    }

    /**
     * The hole that mattered most: the first URL was validated while the HTTP client was
     * told to follow redirects on its own, so a supplier host could answer
     * "302 -> http://169.254.169.254/" and be fetched unchecked.
     */
    @Test
    void redirectsAreValidatedToo() {
        URI current = URI.create("https://example.com/product/1");

        assertThatThrownBy(() -> SsrfGuard.validateRedirect(current, "http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.validateRedirect(current, "http://127.0.0.1:8080/api/admin/users"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.validateRedirect(current, "file:///etc/passwd"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);

        // A relative redirect resolves against the current URL and stays allowed.
        assertThat(SsrfGuard.validateRedirect(current, "/product/2").toString())
                .isEqualTo("https://example.com/product/2");
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> SsrfGuard.validate(null))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.validate("   "))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.validate("https://"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }
}
