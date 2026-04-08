package com.warehouse.assistant.store.dto;

import java.util.Map;

/**
 * Structured action attached to an assistant reply. The frontend widget
 * dispatches these through its {@code onSuggestedAction} handler rather than
 * parsing the markdown.
 *
 * Known types:
 * <ul>
 *   <li>{@code SHOW_PRODUCT_CARDS}   — payload: list of product cards</li>
 *   <li>{@code ADD_TO_CART}          — payload: {productId, quantity}</li>
 *   <li>{@code NAVIGATE}             — payload: {route}</li>
 *   <li>{@code LOGIN_PROMPT}         — guest limit reached, UI shows login CTA</li>
 *   <li>{@code SHOW_ORDER}           — payload: order summary</li>
 *   <li>{@code SHOW_COMPARISON}      — payload: product comparison table rows</li>
 * </ul>
 */
public class StoreSuggestedAction {
    public String type;
    public String label;
    public String route;
    public Map<String, Object> payload;

    public StoreSuggestedAction() {}

    public StoreSuggestedAction(String type, String label, String route, Map<String, Object> payload) {
        this.type = type;
        this.label = label;
        this.route = route;
        this.payload = payload;
    }

    public static StoreSuggestedAction loginPrompt() {
        StoreSuggestedAction a = new StoreSuggestedAction();
        a.type = "LOGIN_PROMPT";
        a.label = "Üye Girişi";
        a.route = "/giris";
        return a;
    }

    public static StoreSuggestedAction navigate(String label, String route) {
        StoreSuggestedAction a = new StoreSuggestedAction();
        a.type = "NAVIGATE";
        a.label = label;
        a.route = route;
        return a;
    }
}
