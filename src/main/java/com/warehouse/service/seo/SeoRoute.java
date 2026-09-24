package com.warehouse.service.seo;

import java.util.Arrays;
import java.util.Optional;

/**
 * A storefront URL the server can describe without running the SPA.
 *
 * <p>Deliberately normalised to kind + slug: it is the cache key, and keying on the raw
 * request path would let any query string mint a fresh cache entry.</p>
 */
public record SeoRoute(Kind kind, String slug) {

    public enum Kind {
        HOME(""),
        PRODUCT("urun"),
        CATEGORY("kategori"),
        BRAND("marka"),
        CMS("sayfa");

        private final String pathSegment;

        Kind(String pathSegment) {
            this.pathSegment = pathSegment;
        }

        public String pathSegment() {
            return pathSegment;
        }

        static Optional<Kind> fromPathSegment(String segment) {
            return Arrays.stream(values())
                    .filter(k -> k != HOME && k.pathSegment.equals(segment))
                    .findFirst();
        }
    }

    public static SeoRoute home() {
        return new SeoRoute(Kind.HOME, "");
    }

    /** Empty for a section the storefront does not have, so the caller can answer 404. */
    public static Optional<SeoRoute> of(String section, String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Kind.fromPathSegment(section).map(kind -> new SeoRoute(kind, slug));
    }

    public String path() {
        return kind == Kind.HOME ? "/" : "/" + kind.pathSegment + "/" + slug;
    }
}
