package com.warehouse.assistant.core.observability;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.admin.entity.AssistantMessage;
import com.warehouse.assistant.admin.repository.AssistantConversationRepository;
import com.warehouse.assistant.admin.repository.AssistantMessageRepository;
import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.core.config.AssistantProperties;
import com.warehouse.assistant.core.safety.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Single entry point for persisting chat turns and updating per-conversation
 * usage totals. Methods are {@link Async} so they never slow the chat path.
 * <p>
 * Conversation lookup strategy:
 * <ul>
 *   <li><b>Guest</b>: one conversation per guest session id.</li>
 *   <li><b>Customer</b>: one conversation per customer per profile per day —
 *       keeps the dashboard readable without making customers lose context
 *       mid-session (the frontend keeps the last 30 msgs in localStorage, so
 *       the LLM still sees the rolling window).</li>
 *   <li><b>WMS user</b>: same as customer — one per user per profile per day.</li>
 * </ul>
 */
@Service
public class ConversationLogger {

    private static final Logger log = LoggerFactory.getLogger(ConversationLogger.class);

    private final AssistantConversationRepository conversationRepo;
    private final AssistantMessageRepository messageRepo;
    private final CostCalculator costCalculator;
    private final LogSanitizer logSanitizer;
    private final AssistantProperties props;

    public ConversationLogger(AssistantConversationRepository conversationRepo,
                              AssistantMessageRepository messageRepo,
                              CostCalculator costCalculator,
                              LogSanitizer logSanitizer,
                              AssistantProperties props) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.costCalculator = costCalculator;
        this.logSanitizer = logSanitizer;
        this.props = props;
    }

    /**
     * Session-based conversation resolution. When the frontend provides a
     * chatSessionId, use it as the primary key for grouping. Falls back to
     * legacy actor-based resolution when chatSessionId is null.
     */
    @Transactional
    public AssistantConversation resolveBySessionId(String chatSessionId,
                                                     AssistantProfile profile,
                                                     Long userId,
                                                     Long customerId,
                                                     String guestSessionId,
                                                     String username,
                                                     String ipHash,
                                                     String userAgent) {
        if (chatSessionId != null && !chatSessionId.isBlank()) {
            Optional<AssistantConversation> existing =
                    conversationRepo.findTopByChatSessionIdOrderByIdDesc(chatSessionId);
            if (existing.isPresent()) return existing.get();

            // New session — create
            AssistantConversation c = new AssistantConversation();
            c.setProfile(profile);
            c.setChatSessionId(chatSessionId);
            c.setUserId(userId);
            c.setCustomerId(customerId);
            c.setGuestSessionId(guestSessionId);
            c.setUsername(truncate(username, 128));
            c.setIpHash(ipHash);
            c.setUserAgent(truncate(userAgent, 512));
            return conversationRepo.save(c);
        }

        // Fallback: legacy actor-based resolution
        if (customerId != null) return resolveCustomerConversation(customerId, username, ipHash, userAgent);
        if (userId != null) return resolveWmsConversation(userId, username, ipHash, userAgent);
        return resolveGuestConversation(guestSessionId, ipHash, userAgent);
    }

    /** Resolve or create the conversation row for a guest. */
    @Transactional
    public AssistantConversation resolveGuestConversation(String guestSessionId, String ipHash, String userAgent) {
        Optional<AssistantConversation> existing =
                conversationRepo.findTopByGuestSessionIdOrderByIdDesc(guestSessionId);
        return existing.orElseGet(() -> {
            AssistantConversation c = new AssistantConversation();
            c.setProfile(AssistantProfile.STORE);
            c.setGuestSessionId(guestSessionId);
            c.setIpHash(ipHash);
            c.setUserAgent(truncate(userAgent, 512));
            return conversationRepo.save(c);
        });
    }

    /** Resolve or create the conversation row for a store customer. */
    @Transactional
    public AssistantConversation resolveCustomerConversation(long customerId, String username, String ipHash, String userAgent) {
        Optional<AssistantConversation> existing =
                conversationRepo.findTopByCustomerIdAndProfileOrderByIdDesc(customerId, AssistantProfile.STORE);
        if (existing.isPresent() && isSameDay(existing.get().getStartedAt())) {
            return existing.get();
        }
        AssistantConversation c = new AssistantConversation();
        c.setProfile(AssistantProfile.STORE);
        c.setCustomerId(customerId);
        c.setUsername(truncate(username, 128));
        c.setIpHash(ipHash);
        c.setUserAgent(truncate(userAgent, 512));
        return conversationRepo.save(c);
    }

    /** Resolve or create the conversation row for a WMS user. */
    @Transactional
    public AssistantConversation resolveWmsConversation(long userId, String username, String ipHash, String userAgent) {
        Optional<AssistantConversation> existing =
                conversationRepo.findTopByUserIdAndProfileOrderByIdDesc(userId, AssistantProfile.WMS);
        if (existing.isPresent() && isSameDay(existing.get().getStartedAt())) {
            return existing.get();
        }
        AssistantConversation c = new AssistantConversation();
        c.setProfile(AssistantProfile.WMS);
        c.setUserId(userId);
        c.setUsername(truncate(username, 128));
        c.setIpHash(ipHash);
        c.setUserAgent(truncate(userAgent, 512));
        return conversationRepo.save(c);
    }

    /**
     * Persist one full exchange (the user message + the assistant reply) and
     * bump running usage totals on the parent conversation. Runs async on the
     * Spring task executor so the chat path does not block.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExchange(Long conversationId,
                                String userContent,
                                String assistantContent,
                                UsageMetrics usage,
                                String toolCallsJson) {
        if (conversationId == null) return;
        try {
            LocalDateTime now = LocalDateTime.now();

            // Sanitize PII before persistence (KVKK / defense-in-depth)
            String safeUser = props.getSafety().isLogSanitization()
                    ? logSanitizer.sanitize(userContent) : userContent;
            String safeAssistant = props.getSafety().isLogSanitization()
                    ? logSanitizer.sanitize(assistantContent) : assistantContent;

            AssistantMessage userMsg = new AssistantMessage();
            userMsg.setConversationId(conversationId);
            userMsg.setRole("user");
            userMsg.setContent(truncate(safeUser, 16_000));
            userMsg.setCreatedAt(now);
            messageRepo.save(userMsg);

            BigDecimal cost = costCalculator.chatCost(usage.promptTokens(), usage.completionTokens());

            AssistantMessage asstMsg = new AssistantMessage();
            asstMsg.setConversationId(conversationId);
            asstMsg.setRole("assistant");
            asstMsg.setContent(truncate(safeAssistant, 16_000));
            asstMsg.setPromptTokens(usage.promptTokens());
            asstMsg.setCompletionTokens(usage.completionTokens());
            asstMsg.setLatencyMs(usage.latencyMs());
            asstMsg.setCostUsd(cost);
            asstMsg.setToolCalls(toolCallsJson);
            asstMsg.setCreatedAt(now.plusNanos(1_000));
            messageRepo.save(asstMsg);

            conversationRepo.incrementUsage(
                    conversationId,
                    usage.promptTokens(),
                    usage.completionTokens(),
                    cost,
                    now);
        } catch (Exception e) {
            log.error("Failed to persist chat exchange for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    /** Hash an IP with a daily salt so raw addresses are never persisted. */
    public static String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String salted = ip + "|" + LocalDate.now();
            byte[] digest = md.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameDay(LocalDateTime when) {
        return when != null && when.toLocalDate().equals(LocalDate.now());
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
