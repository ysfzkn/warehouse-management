package com.warehouse.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Destination checks for outbound requests whose URL comes from a user.
 *
 * <p>The product-image crawler lets an operator paste a URL that the server then fetches.
 * Without these checks that turns the backend into a proxy into its own network: the
 * cloud metadata endpoint (169.254.169.254), the database, the admin API on localhost —
 * anything reachable from the container but not from the internet.</p>
 *
 * <p>What the previous inline check missed, and this one covers:</p>
 * <ul>
 *   <li><b>Redirects.</b> Only the first URL was validated while
 *       {@code setInstanceFollowRedirects(true)} silently followed a 302 anywhere. An
 *       attacker-controlled host redirecting to {@code http://169.254.169.254/} walked
 *       straight through. {@link #validateRedirect} is meant to be called per hop.</li>
 *   <li><b>Multiple A records.</b> {@code InetAddress.getByName} returns one address; a
 *       host publishing both a public and a private record could pass. All records are
 *       now checked.</li>
 *   <li><b>IPv6.</b> Java's {@code isSiteLocalAddress()} only recognises the deprecated
 *       fec0::/10 for IPv6, so unique-local fc00::/7 was treated as public. IPv4-mapped
 *       addresses ({@code ::ffff:127.0.0.1}) were not unwrapped either.</li>
 *   <li><b>Carrier-grade NAT.</b> The old check tested {@code startsWith("100.64.")},
 *       which is a single /24 out of the 100.64.0.0/10 block.</li>
 * </ul>
 *
 * <p>Not covered: DNS rebinding. The name is resolved here and again by the HTTP stack,
 * so a record with a very short TTL can flip in between. Closing that needs the
 * connection to be pinned to the validated address; the cost/benefit does not justify it
 * for an admin-only crawler, and it is recorded here so the gap is known rather than
 * assumed absent.</p>
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    public static class BlockedTargetException extends RuntimeException {
        public BlockedTargetException(String message) {
            super(message);
        }
    }

    /** Maximum redirect hops to follow; each one is re-validated. */
    public static final int MAX_REDIRECTS = 5;

    /**
     * Validates scheme and destination address of a URL the server is about to fetch.
     *
     * @throws BlockedTargetException when the target is not a public http(s) endpoint
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BlockedTargetException("URL boş olamaz");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BlockedTargetException("Geçersiz URL formatı");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BlockedTargetException("Sadece http(s) URL'leri kabul edilir");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedTargetException("Host bulunamadı");
        }
        assertPublicAddress(host);
        return uri;
    }

    /**
     * Validates a {@code Location} header before following it. Relative locations are
     * resolved against the URL that produced them.
     */
    public static URI validateRedirect(URI current, String location) {
        if (location == null || location.isBlank()) {
            throw new BlockedTargetException("Yönlendirme hedefi boş");
        }
        URI target;
        try {
            target = current.resolve(location.trim());
        } catch (Exception e) {
            throw new BlockedTargetException("Geçersiz yönlendirme hedefi");
        }
        return validate(target.toString());
    }

    /** Rejects the host if <em>any</em> address it resolves to is non-public. */
    public static void assertPublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.toLowerCase(Locale.ROOT));
        } catch (UnknownHostException e) {
            throw new BlockedTargetException("Host çözülemedi: " + host);
        }
        if (addresses.length == 0) {
            throw new BlockedTargetException("Host çözülemedi: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new BlockedTargetException(
                        "Yerel/özel ağ adresleri engelli (SSRF koruması): " + host);
            }
        }
    }

    private static boolean isPublic(InetAddress address) {
        InetAddress addr = unwrapIpv4Mapped(address);

        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = addr.getAddress();
        if (addr instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 — carrier-grade NAT, routable inside many hosting networks.
            if (first == 100 && second >= 64 && second <= 127) return false;
            // 192.0.0.0/24 IETF protocol assignments, 192.0.2.0/24 TEST-NET-1,
            // 198.18.0.0/15 benchmarking, 198.51.100.0/24 TEST-NET-2,
            // 203.0.113.0/24 TEST-NET-3 — none are legitimate fetch targets.
            if (first == 192 && second == 0 && (bytes[2] & 0xFF) <= 2) return false;
            if (first == 198 && (second == 18 || second == 19)) return false;
            if (first == 198 && second == 51 && (bytes[2] & 0xFF) == 100) return false;
            if (first == 203 && second == 0 && (bytes[2] & 0xFF) == 113) return false;
            // 240.0.0.0/4 reserved, and 0.0.0.0/8.
            if (first >= 240 || first == 0) return false;
            return true;
        }

        if (addr instanceof Inet6Address) {
            int first = bytes[0] & 0xFF;
            // fc00::/7 unique local — isSiteLocalAddress() only knows the deprecated fec0::/10.
            if ((first & 0xFE) == 0xFC) return false;
            // 2001:db8::/32 documentation range.
            if (first == 0x20 && (bytes[1] & 0xFF) == 0x01
                    && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) {
                return false;
            }
            return true;
        }
        return false;
    }

    /** {@code ::ffff:127.0.0.1} must be judged as 127.0.0.1, not as an opaque v6 address. */
    private static InetAddress unwrapIpv4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address)) return address;
        byte[] b = address.getAddress();
        if (b.length != 16) return address;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return address;
        }
        if ((b[10] & 0xFF) != 0xFF || (b[11] & 0xFF) != 0xFF) return address;
        try {
            return InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
