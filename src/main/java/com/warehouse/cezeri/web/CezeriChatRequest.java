package com.warehouse.cezeri.web;

import java.util.List;

public class CezeriChatRequest {

    /**
     * Conversation messages in chronological order.
     * Supported roles: "user", "assistant".
     */
    public List<CezeriMessage> messages;

    /**
     * Frontend-controlled guardrail: when false (default), Cezeri must not run
     * mutation tools (create/update/delete). Use this when the user clicks a
     * dedicated confirm button in UI.
     */
    public boolean allowMutations;

    /**
     * Lightweight UI context for better help (route, selected warehouse, etc.).
     */
    public CezeriUiContext ui;
}


