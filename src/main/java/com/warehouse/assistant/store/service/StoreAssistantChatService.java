package com.warehouse.assistant.store.service;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.core.api.AssistantChatService;
import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.core.observability.ConversationLogger;
import com.warehouse.assistant.core.observability.UsageMetrics;
import com.warehouse.assistant.core.safety.ContentSafetyPipeline;
import com.warehouse.assistant.core.safety.SafetyCheckResult;
import com.warehouse.assistant.core.security.AssistantContext;
import com.warehouse.assistant.core.security.AssistantContextHolder;
import com.warehouse.assistant.store.dto.StoreChatMessage;
import com.warehouse.assistant.store.dto.StoreChatRequest;
import com.warehouse.assistant.store.dto.StoreChatResponse;
import com.warehouse.assistant.store.prompt.StorePromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration for the Store assistant profile. Responsibilities:
 * <ol>
 *   <li>Build the system prompt via {@link StorePromptService}.</li>
 *   <li>Trim history to the last 30 messages for token budget.</li>
 *   <li>Invoke the shared {@code storeChatClient} with tool calling.</li>
 *   <li>Persist the exchange + usage via {@link ConversationLogger}.</li>
 * </ol>
 * Rate limiting, guest-session handling, and actor resolution live one layer
 * above (in the controller / guard) so this class can stay focused on the
 * LLM flow.
 */
@Service
@Profile("!test")
public class StoreAssistantChatService implements AssistantChatService {

    private static final Logger log = LoggerFactory.getLogger(StoreAssistantChatService.class);

    private final ChatClient chatClient;
    private final StorePromptService promptService;
    private final ConversationLogger conversationLogger;
    private final ContentSafetyPipeline safetyPipeline;

    public StoreAssistantChatService(ChatClient storeChatClient,
                                     StorePromptService promptService,
                                     ConversationLogger conversationLogger,
                                     ContentSafetyPipeline safetyPipeline) {
        this.chatClient = storeChatClient;
        this.promptService = promptService;
        this.conversationLogger = conversationLogger;
        this.safetyPipeline = safetyPipeline;
    }

    @Override
    public AssistantProfile profile() {
        return AssistantProfile.STORE;
    }

    public StoreChatResponse chat(StoreChatRequest request, AssistantConversation conversation) {
        AssistantContext ctx = AssistantContextHolder.get();
        boolean isGuest = ctx == null || ctx.customerId() == null;
        String displayName = ctx != null ? ctx.username() : null;
        String userId = ctx != null && ctx.customerId() != null ? String.valueOf(ctx.customerId()) : "guest";

        // ── Katman 1: Input validation ──
        String lastUser = lastUserMessage(request != null ? request.messages : null);
        SafetyCheckResult inputCheck = safetyPipeline.validateInput(AssistantProfile.STORE, userId, lastUser);
        if (inputCheck.isBlocked()) {
            StoreChatResponse blocked = new StoreChatResponse();
            blocked.message = inputCheck.getBlockReason();
            blocked.suggestedActions = List.of();
            return blocked;
        }

        String system = promptService.buildSystemPrompt(
                isGuest,
                displayName,
                request != null ? request.ui : null,
                request != null ? request.siteName : null);

        List<Message> messages = toSpringAiMessages(request != null ? request.messages : null);
        if (messages.size() > 30) {
            messages = messages.subList(messages.size() - 30, messages.size());
        }

        String assistantText;
        UsageMetrics usage = UsageMetrics.zero();
        long startedAt = System.currentTimeMillis();
        try {
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(system)
                    .messages(messages)
                    .call()
                    .chatResponse();

            assistantText = chatResponse != null && chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : "";
            usage = extractUsage(chatResponse, startedAt);

            // ── Katman 3: Output validation ──
            assistantText = safetyPipeline.validateOutput(AssistantProfile.STORE, userId, assistantText);
        } catch (Exception e) {
            log.error("Store assistant chat failed: {}", e.getMessage());
            assistantText = """
                    Şu an yapay zekâ servisine erişemiyorum. Lütfen birkaç saniye sonra tekrar deneyin;
                    devam ederse destek ekibimize ulaşabilirsiniz.
                    """;
        }

        StoreChatResponse response = new StoreChatResponse();
        response.message = assistantText;
        response.suggestedActions = List.of();

        // Persist async — never blocks the chat path.
        if (conversation != null && conversation.getId() != null) {
            conversationLogger.recordExchange(
                    conversation.getId(),
                    lastUser,
                    assistantText,
                    usage,
                    null);
        }
        return response;
    }

    private UsageMetrics extractUsage(ChatResponse chatResponse, long startedAt) {
        int latency = (int) (System.currentTimeMillis() - startedAt);
        if (chatResponse == null) return new UsageMetrics(0, 0, latency);
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata == null) return new UsageMetrics(0, 0, latency);
        Usage u = metadata.getUsage();
        if (u == null) return new UsageMetrics(0, 0, latency);
        int prompt = u.getPromptTokens() != null ? u.getPromptTokens().intValue() : 0;
        int completion = u.getCompletionTokens() != null ? u.getCompletionTokens().intValue() : 0;
        return new UsageMetrics(prompt, completion, latency);
    }

    private String lastUserMessage(List<StoreChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            StoreChatMessage m = messages.get(i);
            if (m != null && m.content != null && "user".equalsIgnoreCase(m.role)) {
                return m.content;
            }
        }
        return null;
    }

    private List<Message> toSpringAiMessages(List<StoreChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return new ArrayList<>();
        List<Message> out = new ArrayList<>(messages.size());
        for (StoreChatMessage m : messages) {
            if (m == null || m.content == null) continue;
            if ("assistant".equalsIgnoreCase(m.role)) {
                out.add(new AssistantMessage(m.content));
            } else {
                out.add(new UserMessage(m.content));
            }
        }
        return out;
    }
}
