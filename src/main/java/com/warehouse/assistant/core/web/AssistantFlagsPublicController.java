package com.warehouse.assistant.core.web;

import com.warehouse.assistant.core.config.AssistantFlagsService;
import org.springframework.context.annotation.Profile;
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

    private final AssistantFlagsService flagsService;

    public AssistantFlagsPublicController(AssistantFlagsService flagsService) {
        this.flagsService = flagsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFlags() {
        return ResponseEntity.ok(flagsService.getAllFlags());
    }
}
