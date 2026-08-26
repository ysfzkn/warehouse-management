package com.warehouse.util;

/**
 * Clamps client-supplied pagination parameters.
 *
 * <p>Admin list endpoints take {@code page} and {@code size} straight from the query string.
 * Without a ceiling, {@code ?size=1000000} pulls an entire table into memory, and a negative
 * {@code page} makes {@code PageRequest.of} throw — a 500 instead of a 400.</p>
 */
public final class PageLimits {

    /** Largest page a list endpoint will serve. Exports have their own, higher, limit. */
    public static final int MAX_SIZE = 200;
    public static final int DEFAULT_SIZE = 20;

    private PageLimits() {}

    public static int size(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    public static int page(Integer page) {
        if (page == null) return 0;
        return Math.max(0, page);
    }
}
