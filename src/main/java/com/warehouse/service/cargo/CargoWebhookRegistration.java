package com.warehouse.service.cargo;

import java.util.Map;

/**
 * The outcome of asking the carrier to register a webhook.
 *
 * <p>This used to be a bare boolean, so a failed registration reached the admin screen as
 * {@code {"success": false}} and nothing else — the reason lived only in a server log line that
 * an administrator has no way to read. A refused token, a rejected callback URL and a carrier
 * that never answered all looked identical, and all three need different actions.
 *
 * @param webhook the created record when the carrier accepted, otherwise null
 * @param reason  what went wrong, phrased for an administrator, or null on success
 */
public record CargoWebhookRegistration(boolean success, Map<String, Object> webhook, String reason) {

    public static CargoWebhookRegistration ok(Map<String, Object> webhook) {
        return new CargoWebhookRegistration(true, webhook == null ? Map.of() : webhook, null);
    }

    public static CargoWebhookRegistration failed(String reason) {
        return new CargoWebhookRegistration(false, null, reason);
    }

    /**
     * The signing key the carrier issued, if it issued one.
     *
     * <p>The field is undocumented, so all three spellings a carrier plausibly uses are checked.
     * Null is a real answer — it means the key has to come from somewhere else, not that the
     * registration failed.
     */
    public String issuedSecret() {
        if (webhook == null) return null;
        for (String field : new String[]{"secret", "secret_key", "signature_key"}) {
            Object value = webhook.get(field);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}
