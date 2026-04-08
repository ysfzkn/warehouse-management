package com.warehouse.assistant.wms.web;

import com.warehouse.assistant.core.config.AssistantFlagsService;
import com.warehouse.assistant.core.security.AssistantContext;
import com.warehouse.assistant.core.security.AssistantContextHolder;
import com.warehouse.assistant.wms.service.WmsAssistantChatService;
import com.warehouse.util.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WMS assistant REST endpoint.
 * <p>
 * URL is preserved at {@code /api/cezeri/chat} for backward compatibility
 * with the existing frontend widget. The WMS/Store split happens at the
 * service and tool level — not at the URL level — during Phase 0.
 * Phase 1.8 will revisit the path and tighten it under the admin security
 * chain.
 */
@RestController
@RequestMapping("/api/cezeri")
@CrossOrigin(origins = "*")
@Profile("!test")
public class WmsAssistantController {

    private final WmsAssistantChatService chatService;
    private final AssistantFlagsService flagsService;

    public WmsAssistantController(WmsAssistantChatService chatService,
                                  AssistantFlagsService flagsService) {
        this.chatService = chatService;
        this.flagsService = flagsService;
    }

    @PostMapping("/chat")
    public ResponseEntity<WmsChatResponse> chat(@RequestBody(required = false) WmsChatRequest request) {
        // Defense-in-depth: even if a stale frontend still renders the widget,
        // disabling the feature from the admin dashboard must actually block
        // API traffic. Mirrors the visual gating in AdminLayout.
        if (!flagsService.isWmsEnabled()) {
            WmsChatResponse disabled = new WmsChatResponse();
            disabled.message = "Cezeri yönetici asistanı şu anda devre dışı.";
            disabled.suggestedActions = java.util.List.of();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(disabled);
        }

        String username = CurrentUser.usernameOrSystem();
        String role = CurrentUser.getRole();
        boolean allowMutations = request != null && request.allowMutations;

        AssistantContextHolder.set(AssistantContext.wms(username, role, allowMutations));
        try {
            return ResponseEntity.ok(chatService.chat(username, role, request));
        } finally {
            AssistantContextHolder.clear();
        }
    }
}
