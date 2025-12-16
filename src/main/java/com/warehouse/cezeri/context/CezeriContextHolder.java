package com.warehouse.cezeri.context;

/**
 * Request-scoped context for Cezeri tool execution.
 * We intentionally keep this minimal and avoid leaking sensitive data into prompts.
 */
public final class CezeriContextHolder {

    private static final ThreadLocal<CezeriContext> CTX = new ThreadLocal<>();

    private CezeriContextHolder() {}

    public static void set(CezeriContext context) {
        CTX.set(context);
    }

    public static CezeriContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}


