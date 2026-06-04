package com.warehouse.assistant.admin.web;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.admin.repository.AssistantConversationRepository;
import com.warehouse.assistant.admin.repository.AssistantMessageRepository;
import com.warehouse.util.CustomerTokenExtractor;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * KVKK / GDPR compliance: lets authenticated store customers delete their
 * own conversation transcripts. Admin can also delete any conversation via
 * the dashboard.
 * <p>
 * Mounted under {@code /api/store/assistant/privacy} so it goes through the
 * store security chain and is accessible to customers.
 */
@RestController
@RequestMapping("/api/store/assistant/privacy")
@Profile("!test")
public class AssistantPrivacyController {

    private final AssistantConversationRepository conversationRepo;
    private final AssistantMessageRepository messageRepo;
    private final JwtService jwtService;

    public AssistantPrivacyController(AssistantConversationRepository conversationRepo,
                                      AssistantMessageRepository messageRepo,
                                      JwtService jwtService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.jwtService = jwtService;
    }

    /**
     * Customer deletes their own conversation by id. The controller verifies
     * that the conversation belongs to the calling customer before deleting.
     */
    @DeleteMapping("/conversations/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteMyConversation(
            @PathVariable Long id,
            HttpServletRequest request) {

        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        if (customerId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Bu işlem için giriş yapmanız gerekiyor."));
        }

        Optional<AssistantConversation> maybe = conversationRepo.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AssistantConversation conv = maybe.get();
        if (!customerId.equals(conv.getCustomerId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Bu sohbet size ait değil."));
        }

        // Cascade: assistant_message rows are ON DELETE CASCADE in the DB,
        // but we explicitly delete via repo for JPA cache consistency.
        messageRepo.deleteAll(
                messageRepo.findByConversationIdOrderByCreatedAtAsc(id));
        conversationRepo.delete(conv);

        return ResponseEntity.ok(Map.of("message", "Sohbet kaydı başarıyla silindi."));
    }
}
