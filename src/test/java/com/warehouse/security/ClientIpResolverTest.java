package com.warehouse.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every brute-force counter and rate limit is keyed on the value this class returns, so
 * a caller who can influence it can reset any of them at will.
 *
 * <p>The previous implementation read {@code X-Forwarded-For.split(",")[0]} — the
 * left-most entry, which is whatever the client typed. These tests pin the counting
 * direction: with N trusted proxies the genuine peer is the N-th entry from the right,
 * and everything to its left is untrusted noise.</p>
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void withNoProxyTheHeaderIsIgnoredCompletely() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        // A public peer address means nothing is in front of us: the header is noise.
        assertThat(resolver.resolve(request("198.51.100.5", "1.2.3.4")))
                .isEqualTo("198.51.100.5");
    }

    /**
     * Safety net for a misconfigured proxy list. Tomcat's RemoteIpValve leaves the peer
     * address untouched when it does not recognise the proxy — which happened for real
     * with an IPv4-only internal-proxies regex on Railway's IPv6 private network. Every
     * visitor then shares the proxy's address, i.e. one rate-limit bucket for the whole
     * site. A private peer plus a forwarded header is that situation, and reading the
     * appended entry is safe because a client cannot give itself a private peer address.
     */
    @Test
    void aPrivatePeerWithAForwardedHeaderFallsBackToTheAppendedEntry() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        assertThat(resolver.resolve(request("10.0.0.7", "1.2.3.4, 198.51.100.5")))
                .isEqualTo("198.51.100.5");
        assertThat(resolver.resolve(request("fd00::1", "198.51.100.9")))
                .isEqualTo("198.51.100.9");
    }

    /**
     * Loopback is not a proxy. A request that never crossed a network interface has
     * nothing in front of it, so its forwarded header is just caller input — and this is
     * the shape the penetration test uses to prove spoofing cannot buy a fresh bucket.
     */
    @Test
    void aLoopbackPeerNeverTriggersTheFallback() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        assertThat(resolver.resolve(request("127.0.0.1", "203.0.113.7")))
                .isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("0:0:0:0:0:0:0:1", "203.0.113.8")))
                .isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void thatFallbackStillCannotBeSpoofedIntoSeparateBuckets() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        String a = resolver.resolve(request("10.0.0.7", "203.0.113.1, 198.51.100.5"));
        String b = resolver.resolve(request("10.0.0.7", "203.0.113.2, 198.51.100.5"));
        String c = resolver.resolve(request("10.0.0.7", "203.0.113.3, 203.0.113.4, 198.51.100.5"));
        assertThat(a).isEqualTo(b).isEqualTo(c).isEqualTo("198.51.100.5");
    }

    @Test
    void aPrivatePeerWithoutAForwardedHeaderKeepsThePeerAddress() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        assertThat(resolver.resolve(request("10.0.0.7", null))).isEqualTo("10.0.0.7");
    }

    @Test
    void withOneProxyTheLastEntryWins() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        // The edge proxy appended the address it actually saw; anything before it was
        // supplied by the client and must not be believed.
        assertThat(resolver.resolve(request("10.0.0.1", "1.2.3.4, 198.51.100.5")))
                .isEqualTo("198.51.100.5");
    }

    @Test
    void spoofingCannotMintAFreshBucket() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        String first = resolver.resolve(request("10.0.0.1", "203.0.113.1, 198.51.100.5"));
        String second = resolver.resolve(request("10.0.0.1", "203.0.113.2, 198.51.100.5"));
        String third = resolver.resolve(request("10.0.0.1", "203.0.113.3, 203.0.113.4, 198.51.100.5"));
        assertThat(first).isEqualTo(second).isEqualTo(third)
                .as("the same real client must always land in the same bucket");
    }

    @Test
    void withTwoProxiesTheSecondFromTheRightWins() {
        ClientIpResolver resolver = new ClientIpResolver(2);
        assertThat(resolver.resolve(request("10.0.0.1", "1.2.3.4, 198.51.100.5, 10.0.0.9")))
                .isEqualTo("198.51.100.5");
    }

    @Test
    void aShortHeaderDegradesToItsLeftMostEntryRatherThanThrowing() {
        ClientIpResolver resolver = new ClientIpResolver(3);
        assertThat(resolver.resolve(request("10.0.0.1", "198.51.100.5")))
                .isEqualTo("198.51.100.5");
    }

    @Test
    void garbageInTheHeaderFallsBackToTheSocketAddress() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        assertThat(resolver.resolve(request("198.51.100.5", "not-an-ip")))
                .isEqualTo("198.51.100.5");
        assertThat(resolver.resolve(request("198.51.100.5", "<script>alert(1)</script>")))
                .isEqualTo("198.51.100.5");
        assertThat(resolver.resolve(request("198.51.100.5", "")))
                .isEqualTo("198.51.100.5");
    }

    @Test
    void ipv6IsAccepted() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        assertThat(resolver.resolve(request("10.0.0.1", "2001:db8::1")))
                .isEqualTo("2001:db8::1");
    }

    @Test
    void neverReturnsNull() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        MockHttpServletRequest noAddress = new MockHttpServletRequest();
        noAddress.setRemoteAddr(null);
        assertThat(resolver.resolve(noAddress)).isNotNull();
        assertThat(resolver.resolve(null)).isNotNull();
    }
}
