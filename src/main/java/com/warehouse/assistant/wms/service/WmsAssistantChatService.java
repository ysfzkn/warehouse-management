package com.warehouse.assistant.wms.service;

import com.warehouse.assistant.core.api.AssistantChatService;
import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.wms.policy.WmsRolePolicy;
import com.warehouse.assistant.wms.prompt.WmsPromptService;
import com.warehouse.assistant.wms.web.WmsChatMessage;
import com.warehouse.assistant.wms.web.WmsChatRequest;
import com.warehouse.assistant.wms.web.WmsChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WMS assistant chat orchestration (Spring AI + Azure OpenAI + tool calling).
 * Behaviorally identical to the original {@code CezeriChatService}; only the
 * package / class / bean names changed during the Phase 0 refactor.
 */
@Service
@Profile("!test")
public class WmsAssistantChatService implements AssistantChatService {

    private static final Logger log = LoggerFactory.getLogger(WmsAssistantChatService.class);

    private final ChatClient chatClientUser;
    private final ChatClient chatClientAdmin;
    private final WmsPromptService promptService;

    public WmsAssistantChatService(ChatClient wmsChatClientUser,
                                   ChatClient wmsChatClientAdmin,
                                   WmsPromptService promptService) {
        this.chatClientUser = wmsChatClientUser;
        this.chatClientAdmin = wmsChatClientAdmin;
        this.promptService = promptService;
    }

    @Override
    public AssistantProfile profile() {
        return AssistantProfile.WMS;
    }

    public WmsChatResponse chat(String username, String role, WmsChatRequest request) {
        boolean allowMutations = request != null && request.allowMutations;
        String system = promptService.buildSystemPrompt(username, role, allowMutations, request != null ? request.ui : null);

        String lastUser = lastUserMessage(request != null ? request.messages : null);
        // Deterministic capabilities response (no hallucination)
        if (WmsRolePolicy.isCapabilitiesQuestion(lastUser)) {
            WmsChatResponse resp = new WmsChatResponse();
            resp.message = WmsRolePolicy.capabilitiesForRole(role);
            resp.suggestedActions = List.of();
            return resp;
        }

        // If non-admin asks for obviously admin-only items, respond politely and list allowed actions.
        if (!WmsRolePolicy.isAdmin(role) && WmsRolePolicy.looksAdminOnlyRequest(lastUser)) {
            WmsChatResponse resp = new WmsChatResponse();
            resp.message = """
                    Bu işlem için **Yönetici yetkisi** gerekiyor. Yetkiniz olmadığı için bunu sizin adınıza gerçekleştiremiyorum.

                                        Sizin yapabilecekleriniz:
                                        %s
                    """.formatted(WmsRolePolicy.capabilitiesForRole(role));
            resp.suggestedActions = List.of();
            return resp;
        }

        List<Message> messages = toSpringAiMessages(request != null ? request.messages : null);
        if (messages.size() > 30) {
            messages = messages.subList(messages.size() - 30, messages.size());
        }

        String assistantText;
        try {
            ChatClient client = WmsRolePolicy.isAdmin(role) ? chatClientAdmin : chatClientUser;
            assistantText = client
                    .prompt()
                    .system(system)
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("WMS assistant chat failed. role={}, username={}", role, username, e);
            assistantText = """
                    Şu anda yapay zekâ modeline erişemedim.

                    Lütfen teknik yöneticinize danışınız.
                    """;
        }

        WmsChatResponse resp = new WmsChatResponse();
        resp.message = assistantText;
        resp.suggestedActions = List.of();
        return resp;
    }

    private String lastUserMessage(List<WmsChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            WmsChatMessage m = messages.get(i);
            if (m != null && m.content != null && "user".equalsIgnoreCase(m.role)) {
                return m.content;
            }
        }
        return null;
    }

    private List<Message> toSpringAiMessages(List<WmsChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(m -> m != null && m.content != null)
                .map(m -> {
                    if ("assistant".equalsIgnoreCase(m.role)) {
                        return (Message) new AssistantMessage(m.content);
                    }
                    return (Message) new UserMessage(m.content);
                })
                .toList();
    }
}
