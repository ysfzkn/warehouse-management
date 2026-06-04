package com.warehouse.assistant.core.web;

import com.warehouse.assistant.core.config.AssistantFlagsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny public endpoint the frontend hits on every page load to decide
 * whether to render the assistant widget. Kept outside both the admin and
 * store security chains so both AdminLayout and StoreLayout can call it
 * without auth — the payload is two booleans, nothing sensitive.
 */
@RestController
@RequestMapping("/api/assistant/flags")
@CrossOrigin(origins = "*")
@Profile("!test")
public class AssistantFlagsPublicController {

    private static final Logger log = LoggerFactory.getLogger(AssistantFlagsPublicController.class);

    private final AssistantFlagsService flagsService;

    public AssistantFlagsPublicController(AssistantFlagsService flagsService) {
        this.flagsService = flagsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFlags(HttpServletRequest request) {
        Map<String, Boolean> flags = flagsService.getAllFlags();
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        log.debug("[AssistantFlags] GET /api/assistant/flags → {} (origin={}, referer={}, ip={})",
                flags, origin, referer, request.getRemoteAddr());
        // Never cache flags — admin toggles must propagate immediately to all clients.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(flags);
    }
}
