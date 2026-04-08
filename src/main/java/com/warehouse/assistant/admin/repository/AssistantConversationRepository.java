package com.warehouse.assistant.admin.repository;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.core.api.AssistantProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {

    Optional<AssistantConversation> findTopByGuestSessionIdOrderByIdDesc(String guestSessionId);

    Optional<AssistantConversation> findTopByCustomerIdAndProfileOrderByIdDesc(Long customerId, AssistantProfile profile);

    Optional<AssistantConversation> findTopByUserIdAndProfileOrderByIdDesc(Long userId, AssistantProfile profile);

    Page<AssistantConversation> findByProfileOrderByStartedAtDesc(AssistantProfile profile, Pageable pageable);

    Page<AssistantConversation> findAllByOrderByStartedAtDesc(Pageable pageable);

    long countByProfileAndStartedAtAfter(AssistantProfile profile, LocalDateTime after);

    @Query("select coalesce(sum(c.totalCostUsd), 0) from AssistantConversation c where c.profile = :profile and c.startedAt > :after")
    BigDecimal sumCostByProfileSince(@Param("profile") AssistantProfile profile, @Param("after") LocalDateTime after);

    @Modifying
    @Query("""
            update AssistantConversation c
               set c.messageCount = c.messageCount + 1,
                   c.totalPromptTokens = c.totalPromptTokens + :promptTokens,
                   c.totalCompletionTokens = c.totalCompletionTokens + :completionTokens,
                   c.totalCostUsd = c.totalCostUsd + :costUsd,
                   c.lastActivityAt = :now
             where c.id = :id
           """)
    int incrementUsage(@Param("id") Long id,
                       @Param("promptTokens") long promptTokens,
                       @Param("completionTokens") long completionTokens,
                       @Param("costUsd") BigDecimal costUsd,
                       @Param("now") LocalDateTime now);
}
