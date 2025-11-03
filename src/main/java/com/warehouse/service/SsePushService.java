package com.warehouse.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Service for managing SSE subscriptions and broadcasting lightweight updates
 * to all connected clients. Implementations should be thread-safe.
 */
public interface SsePushService {

    /**
     * Registers a new client subscription and sends an initial snapshot event
     * containing current counts. The returned emitter is kept until client
     * disconnects or the connection times out.
     *
     * @return active {@link SseEmitter}
     */
    SseEmitter subscribe();

    /**
     * Broadcasts the latest snapshot (unread notification count and low stock count)
     * to all currently connected clients. Implementations should drop emitters that
     * fail to receive the update.
     */
    void broadcastCounts();
}


