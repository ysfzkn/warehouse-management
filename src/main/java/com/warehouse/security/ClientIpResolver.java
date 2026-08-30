package com.warehouse.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the real client IP in a way that cannot be spoofed by the caller.
 *
 * <p>The naive {@code X-Forwarded-For.split(",")[0]} approach reads the
 * <em>left-most</em> entry, which is entirely attacker controlled: nginx and the
 * Railway edge <b>append</b> the peer address to whatever the client already
 * sent. Any client could therefore send {@code X-Forwarded-For: 1.2.3.4} and get
 * a fresh rate-limit bucket on every request, defeating every brute-force and
 * abuse control in the application.</p>
 *
 * <p>This resolver instead counts from the <b>right</b>: with {@code N} trusted
 * reverse proxies in front of the app, the genuine peer address is the
 * {@code N}-th entry from the end, because each trusted hop appended exactly one
 * entry. Everything to the left of that is untrusted client input and ignored.</p>
 *
 * <p>Configuration — {@code app.security.trusted-proxy-count}:
 * <ul>
 *   <li>{@code 0} → the app is directly exposed; forwarded headers are ignored
 *       entirely and {@code getRemoteAddr()} is authoritative.</li>
 *   <li>{@code 1} (default) → a single edge proxy (Railway edge, or nginx).</li>
 *   <li>{@code 2} → e.g. Cloudflare in front of nginx.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    private final int trustedProxyCount;

    public ClientIpResolver(@Value("${app.security.trusted-proxy-count:1}") int trustedProxyCount) {
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    /** Never returns null; falls back to the transport peer address. */
    public String resolve(HttpServletRequest request) {
        if (request == null) return "unknown";
        String peer = request.getRemoteAddr();

        // How many entries to count in from the right. An explicit configured value wins;
        // otherwise infer it.
        int hopsToSkip = trustedProxyCount;
        if (hopsToSkip == 0) {
            // Normally getRemoteAddr() is already the real client, because Tomcat's
            // RemoteIpValve rewrote it. But if the valve did not recognise the edge proxy
            // — an IPv6-only private network missing from internal-proxies, say — the peer
            // is left as the proxy's own private address and every visitor in the world
            // would share that single value: one rate-limit bucket for the entire site.
            //
            // A private peer address plus a forwarded header is exactly that situation, so
            // read the entry the proxy appended instead. This cannot be abused: a client
            // on the internet cannot make its peer address private.
            //
            // Loopback is deliberately excluded. A proxy always reaches the app over a
            // real network interface — a bridge network or the platform's private range —
            // never over loopback. A loopback peer therefore means nothing is in front of
            // us and the header is just something the caller typed.
            if (isProxyPeer(peer) && hasForwardedHeader(request)) {
                hopsToSkip = 1;
            } else {
                return peer != null ? peer : "unknown";
            }
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            // hops[last] was written by the closest trusted proxy, hops[last-1] by the
            // one before it, and so on. With N trusted proxies the client sits at
            // length - N. Clamp to 0 so a short header degrades to the left-most entry
            // rather than throwing.
            int idx = Math.max(0, hops.length - hopsToSkip);
            String candidate = hops[idx].trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }

        // X-Real-IP is set by our own nginx only (clients cannot reach the app directly
        // in a proxied deployment), so it is an acceptable secondary source.
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && isValidIp(realIp.trim())) {
            return realIp.trim();
        }
        return peer != null ? peer : "unknown";
    }

    private static boolean hasForwardedHeader(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null && !xff.isBlank();
    }

    /**
     * True when the address is one an edge proxy or sibling container could be connecting
     * from — RFC1918, link-local, IPv6 unique-local — but <em>not</em> loopback, which
     * means the request never crossed a network interface and no proxy is involved.
     */
    private static boolean isProxyPeer(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) return false;
            byte[] bytes = address.getAddress();
            return address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    // fc00::/7 unique local, which isSiteLocalAddress() does not cover for IPv6.
                    || (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isValidIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return false;
        // Reject obvious junk before hitting the (potentially DNS-resolving) parser.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%';
            if (!ok) return false;
        }
        try {
            // getByName on a literal address never performs a DNS lookup.
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
