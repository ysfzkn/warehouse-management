package com.warehouse.assistant.wms.web;

/**
 * Simple UI action suggestions. The frontend may interpret these to navigate or open modals.
 * This is NOT executed automatically; it's a suggestion payload for UX.
 */
public class WmsSuggestedAction {
    public String type; // e.g. "NAVIGATE", "OPEN_MODAL", "APPLY_FILTER"
    public String label;
    public String route;
    public Object payload;
}
