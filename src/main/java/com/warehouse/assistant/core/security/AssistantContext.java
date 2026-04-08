package com.warehouse.assistant.core.security;

import com.warehouse.assistant.core.api.AssistantProfile;

/**
 * Request-scoped execution context for any assistant profile.
 * <p>
 * Fields are a superset of what any single profile needs — WMS fills
 * {@code userId} and {@code role}, Store fills either {@code customerId}
 * (authenticated) or {@code guestSessionId} (anonymous). Tools that care about
 * identity should read whichever fields belong to their profile and ignore
 * the rest.
 * <p>
 * Deliberately minimal: we avoid leaking personal data (email, phone, full
 * name) into the context because it would then end up in prompts and log files.
 */
public record AssistantContext(
        AssistantProfile profile,
        String username,
        String role,
        boolean allowMutations,
        Long userId,
        Long customerId,
        String guestSessionId
) {

    // ---------- factories ----------

    public static AssistantContext wms(String username, String role, boolean allowMutations) {
        return new AssistantContext(AssistantProfile.WMS, username, role, allowMutations, null, null, null);
    }

    public static AssistantContext wms(String username, String role, boolean allowMutations, Long userId) {
        return new AssistantContext(AssistantProfile.WMS, username, role, allowMutations, userId, null, null);
    }

    public static AssistantContext storeCustomer(Long customerId, String username) {
        return new AssistantContext(AssistantProfile.STORE, username, "CUSTOMER", false, null, customerId, null);
    }

    public static AssistantContext storeGuest(String guestSessionId) {
        return new AssistantContext(AssistantProfile.STORE, null, "GUEST", false, null, null, guestSessionId);
    }

    // ---------- convenience ----------

    public boolean isAuthenticated() {
        return profile == AssistantProfile.WMS
                ? userId != null || (username != null && !username.isBlank() && !"system".equals(username))
                : customerId != null;
    }

    public boolean isGuest() {
        return profile == AssistantProfile.STORE && customerId == null;
    }
}
