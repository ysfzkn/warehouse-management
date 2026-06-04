package com.warehouse.assistant.wms.web;

import java.util.List;

public class WmsChatRequest {

    /**
     * Conversation messages in chronological order.
     * Supported roles: "user", "assistant".
     */
    public List<WmsChatMessage> messages;

    /**
     * Frontend-controlled guardrail: when false (default), the assistant must not run
     * mutation tools (create/update/delete). Use this when the user clicks a
     * dedicated confirm button in UI.
     */
    public boolean allowMutations;

    /**
     * Lightweight UI context for better help (route, selected warehouse, etc.).
     */
    public WmsUiContext ui;

    /** Frontend-generated session ID for conversation grouping. */
    public String chatSessionId;
}
