package com.warehouse.cezeri.service;

import com.warehouse.cezeri.policy.CezeriRolePolicy;
import com.warehouse.cezeri.prompt.CezeriPromptService;
import com.warehouse.cezeri.web.CezeriChatRequest;
import com.warehouse.cezeri.web.CezeriChatResponse;
import com.warehouse.cezeri.web.CezeriMessage;
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
 * Cezeri chat orchestration (Spring AI + Azure OpenAI/Ollama + tool calling).
 * Provider selection via app.ai.provider property.
 */
@Service
@Profile("!test")
public class CezeriChatService {

    private static final Logger log = LoggerFactory.getLogger(CezeriChatService.class);

    private final ChatClient chatClientUser;
    private final ChatClient chatClientAdmin;
    private final CezeriPromptService promptService;

    public CezeriChatService(ChatClient cezeriChatClientUser,
                             ChatClient cezeriChatClientAdmin,
                             CezeriPromptService promptService) {
        this.chatClientUser = cezeriChatClientUser;
        this.chatClientAdmin = cezeriChatClientAdmin;
        this.promptService = promptService;
    }

    public CezeriChatResponse chat(String username, String role, CezeriChatRequest request) {
        boolean allowMutations = request != null && request.allowMutations;
        String system = promptService.buildSystemPrompt(username, role, allowMutations, request != null ? request.ui : null);

        String lastUser = lastUserMessage(request != null ? request.messages : null);
        // Deterministic capabilities response (no hallucination)
        if (CezeriRolePolicy.isCapabilitiesQuestion(lastUser)) {
            CezeriChatResponse resp = new CezeriChatResponse();
            resp.message = CezeriRolePolicy.capabilitiesForRole(role);
            resp.suggestedActions = List.of();
            return resp;
        }

        // If non-admin asks for obviously admin-only items, respond politely and list allowed actions.
        if (!CezeriRolePolicy.isAdmin(role) && CezeriRolePolicy.looksAdminOnlyRequest(lastUser)) {
            CezeriChatResponse resp = new CezeriChatResponse();
            resp.message = """
                    Bu işlem için **Yönetici yetkisi** gerekiyor. Yetkiniz olmadığı için bunu sizin adınıza gerçekleştiremiyorum.
                    
                                        Sizin yapabilecekleriniz:
                                        %s
                    """.formatted(CezeriRolePolicy.capabilitiesForRole(role));
            resp.suggestedActions = List.of();
            return resp;
        }

        List<Message> messages = toSpringAiMessages(request != null ? request.messages : null);
        if (messages.size() > 30) {
            messages = messages.subList(messages.size() - 30, messages.size());
        }

        String assistantText;
        try {
            ChatClient client = CezeriRolePolicy.isAdmin(role) ? chatClientAdmin : chatClientUser;
            assistantText = client
                    .prompt()
                    .system(system)
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Cezeri chat failed. role={}, username={}", role, username, e);
            assistantText = """
                    Şu anda yapay zekâ modeline erişemedim.
                    
                    Lütfen teknik yöneticinize danışınız.
                    """;
        }

        CezeriChatResponse resp = new CezeriChatResponse();
        resp.message = assistantText;
        resp.suggestedActions = List.of();
        return resp;
    }

    private String lastUserMessage(List<CezeriMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CezeriMessage m = messages.get(i);
            if (m != null && m.content != null && "user".equalsIgnoreCase(m.role)) {
                return m.content;
            }
        }
        return null;
    }

    private List<Message> toSpringAiMessages(List<CezeriMessage> messages) {
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


