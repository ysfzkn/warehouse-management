package com.warehouse.assistant.admin.entity;

import com.warehouse.assistant.core.api.AssistantProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per chat session. Exactly one of (userId, customerId, guestSessionId)
 * is expected to be populated. Running totals are denormalized for dashboard
 * performance — see {@code AssistantMessage} for the authoritative per-turn
 * data.
 */
@Entity
@Table(name = "assistant_conversation")
@Data
@NoArgsConstructor
public class AssistantConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile", nullable = false, length = 16)
    private AssistantProfile profile;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "guest_session_id", length = 64)
    private String guestSessionId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "total_prompt_tokens", nullable = false)
    private long totalPromptTokens;

    @Column(name = "total_completion_tokens", nullable = false)
    private long totalCompletionTokens;

    @Column(name = "total_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (lastActivityAt == null) lastActivityAt = now;
        if (totalCostUsd == null) totalCostUsd = BigDecimal.ZERO;
    }
}
