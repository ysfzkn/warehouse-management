package com.warehouse.assistant.store.dto;

import java.util.List;

public class StoreChatRequest {

    /** Conversation history (chronological). Supported roles: user, assistant. */
    public List<StoreChatMessage> messages;

    /** Lightweight UI context: current route, selected product, etc. */
    public StoreUiContext ui;

    /** Store branding name (from site settings) — injected into the prompt's persona block. */
    public String siteName;

    /** Frontend-generated session ID for conversation grouping. New UUID per browser session. */
    public String chatSessionId;
}
