package com.warehouse.assistant.core.security;

/**
 * ThreadLocal holder for the current request's {@link AssistantContext}.
 * <p>
 * Controllers set the context at the beginning of a request and clear it in
 * a finally block; tools may read it during LLM tool-call execution. Because
 * Spring AI's tool invocations run on the same request thread, a
 * ThreadLocal is sufficient — if we ever move to reactive streaming we will
 * need to switch to {@code Reactor Context} instead.
 */
public final class AssistantContextHolder {

    private static final ThreadLocal<AssistantContext> CTX = new ThreadLocal<>();

    private AssistantContextHolder() {}

    public static void set(AssistantContext context) {
        CTX.set(context);
    }

    public static AssistantContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
