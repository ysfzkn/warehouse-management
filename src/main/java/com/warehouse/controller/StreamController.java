package com.warehouse.controller;

import com.warehouse.security.StreamTicketService;
import com.warehouse.service.SsePushService;
import com.warehouse.util.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Server-Sent Events endpoint for pushing lightweight updates (unread notification
 * count, low stock count) to connected clients.
 *
 * <p>Connection flow: {@code POST /api/admin/stream/ticket} with the normal Bearer
 * token returns a single-use ticket, then the client opens
 * {@code GET /api/admin/stream?ticket=...}. Putting the JWT itself in the query string
 * — as the previous {@code ?token=} contract did — leaked a full-lifetime admin
 * credential into access logs, browser history and {@code Referer} headers.</p>
 */
@RestController
@RequestMapping(value = "/api/admin/stream")
public class StreamController {

    private final SsePushService ssePushService;
    private final StreamTicketService streamTicketService;

    public StreamController(SsePushService ssePushService, StreamTicketService streamTicketService) {
        this.ssePushService = ssePushService;
        this.streamTicketService = streamTicketService;
    }

    @PostMapping(value = "/ticket", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ticket() {
        String ticket = streamTicketService.issue(CurrentUser.usernameOrSystem(), CurrentUser.getRole());
        return ResponseEntity.ok(Map.of(
                "ticket", ticket,
                "expiresInSeconds", streamTicketService.ttlSeconds()));
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return ssePushService.subscribe();
    }
}
