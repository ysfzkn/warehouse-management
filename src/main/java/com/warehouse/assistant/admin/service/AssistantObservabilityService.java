package com.warehouse.assistant.admin.service;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.admin.entity.AssistantMessage;
import com.warehouse.assistant.admin.repository.AssistantConversationRepository;
import com.warehouse.assistant.admin.repository.AssistantMessageRepository;
import com.warehouse.assistant.core.api.AssistantProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only service backing the admin dashboard + logs pages.
 * Keeps SQL in the repositories and simple aggregation here.
 */
@Service
public class AssistantObservabilityService {

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;

    public AssistantObservabilityService(AssistantConversationRepository conversationRepository,
                                         AssistantMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    // -------------------------------------------------------------------------
    // Dashboard KPIs
    // -------------------------------------------------------------------------

    public Map<String, Object> getDashboardKpis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        Map<String, Object> out = new HashMap<>();
        for (AssistantProfile profile : AssistantProfile.values()) {
            Map<String, Object> profileStats = new HashMap<>();
            profileStats.put("today",      conversationRepository.countByProfileAndStartedAtAfter(profile, startOfDay));
            profileStats.put("last7Days",  conversationRepository.countByProfileAndStartedAtAfter(profile, sevenDaysAgo));
            profileStats.put("last30Days", conversationRepository.countByProfileAndStartedAtAfter(profile, thirtyDaysAgo));
            profileStats.put("costToday",      safe(conversationRepository.sumCostByProfileSince(profile, startOfDay)));
            profileStats.put("costLast7Days",  safe(conversationRepository.sumCostByProfileSince(profile, sevenDaysAgo)));
            profileStats.put("costLast30Days", safe(conversationRepository.sumCostByProfileSince(profile, thirtyDaysAgo)));
            out.put(profile.name(), profileStats);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Conversation logs
    // -------------------------------------------------------------------------

    public Page<AssistantConversation> listConversations(AssistantProfile profile, Pageable pageable) {
        if (profile == null) {
            return conversationRepository.findAllByOrderByStartedAtDesc(pageable);
        }
        return conversationRepository.findByProfileOrderByStartedAtDesc(profile, pageable);
    }

    public Map<String, Object> getConversationDetail(Long conversationId) {
        Optional<AssistantConversation> conv = conversationRepository.findById(conversationId);
        if (conv.isEmpty()) return null;
        List<AssistantMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("conversation", conv.get());
        detail.put("messages", messages);
        return detail;
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
