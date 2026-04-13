package com.warehouse.assistant.wms.web;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.core.config.AssistantFlagsService;
import com.warehouse.assistant.core.observability.ConversationLogger;
import com.warehouse.assistant.core.security.AssistantContext;
import com.warehouse.assistant.core.security.AssistantContextHolder;
import com.warehouse.assistant.wms.service.WmsAssistantChatService;
import com.warehouse.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ConversationLogger conversationLogger;

    public WmsAssistantController(WmsAssistantChatService chatService,
                                  AssistantFlagsService flagsService,
                                  ConversationLogger conversationLogger) {
        this.chatService = chatService;
        this.flagsService = flagsService;
        this.conversationLogger = conversationLogger;
    }

    @PostMapping("/chat")
    public ResponseEntity<WmsChatResponse> chat(@RequestBody(required = false) WmsChatRequest request,
                                                HttpServletRequest httpRequest) {
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
            // Resolve conversation for logging (session-based)
            String chatSessionId = request != null ? request.chatSessionId : null;
            String ipHash = ConversationLogger.hashIp(httpRequest.getRemoteAddr());
            String userAgent = httpRequest.getHeader("User-Agent");
            AssistantConversation conversation = conversationLogger.resolveBySessionId(
                    chatSessionId,
                    AssistantProfile.WMS,
                    null, null, null,
                    username,
                    ipHash,
                    userAgent);

            WmsChatResponse response = chatService.chat(username, role, request, conversation);
            return ResponseEntity.ok(response);
        } finally {
            AssistantContextHolder.clear();
        }
    }
}
