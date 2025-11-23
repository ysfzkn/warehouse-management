package com.warehouse.controller;

import com.warehouse.service.SsePushService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes a Server-Sent Events endpoint for pushing lightweight updates
 * (such as unread notification count and low stock count) to connected clients.
 *
 * Clients should connect with: GET /api/stream?token=JWT
 * The JWT token is validated by the security filter. On connect, an initial
 * snapshot event is sent, and subsequent updates are broadcast on changes.
 */
@RestController
@RequestMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@CrossOrigin(origins = "*")
public class StreamController {

    private final SsePushService ssePushService;

    public StreamController(SsePushService ssePushService) {
        this.ssePushService = ssePushService;
    }

    @GetMapping
    public SseEmitter subscribe() {
        return ssePushService.subscribe();
    }
}









