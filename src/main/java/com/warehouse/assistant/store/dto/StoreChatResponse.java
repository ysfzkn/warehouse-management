package com.warehouse.assistant.store.dto;

import java.util.List;

public class StoreChatResponse {

    /** Assistant reply text (Turkish markdown). */
    public String message;

    /** Structured actions for the frontend widget. */
    public List<StoreSuggestedAction> suggestedActions;

    /**
     * When non-null, indicates the request was rate-limited or the guest
     * reached their free-session cap. The frontend uses this to decide
     * whether to render a friendly login CTA or a cooldown notice.
     */
    public RateLimitInfo rateLimit;

    public static class RateLimitInfo {
        public String scope;            // e.g. GUEST_SESSION, CUSTOMER_HOURLY
        public long retryAfterSeconds;
        public boolean reachedGuestLimit;
        public String friendlyMessage;  // TR, shown directly in the chat bubble
    }
}
