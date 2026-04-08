package com.warehouse.assistant.store.web;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.core.config.AssistantFlagsService;
import com.warehouse.assistant.core.observability.ConversationLogger;
import com.warehouse.assistant.core.ratelimit.RateLimitDecision;
import com.warehouse.assistant.core.security.AssistantContext;
import com.warehouse.assistant.core.security.AssistantContextHolder;
import com.warehouse.assistant.store.dto.StoreChatRequest;
import com.warehouse.assistant.store.dto.StoreChatResponse;
import com.warehouse.assistant.store.dto.StoreSuggestedAction;
import com.warehouse.assistant.store.security.StoreAssistantActor;
import com.warehouse.assistant.store.security.StoreAssistantGuard;
import com.warehouse.assistant.store.service.StoreAssistantChatService;
import com.warehouse.entity.Customer;
import com.warehouse.repository.CustomerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Storefront assistant REST endpoint. Flow:
 * <pre>
 *   1. Guard resolves actor (guest cookie or customer JWT).
 *   2. Guard runs the rate-limit ladder.
 *   3. If rate-limited, we still return HTTP 200 (so the widget can render
 *      the friendly Turkish message inline) with a {@code rateLimit} payload.
 *   4. Otherwise set the {@code AssistantContextHolder}, resolve / create the
 *      {@code AssistantConversation}, delegate to the chat service, and
 *      persist the exchange asynchronously.
 * </pre>
 */
@RestController
@RequestMapping("/api/store/assistant")
@CrossOrigin(origins = "*")
@Profile("!test")
public class StoreAssistantController {

    private final StoreAssistantGuard guard;
    private final StoreAssistantChatService chatService;
    private final ConversationLogger conversationLogger;
    private final CustomerRepository customerRepository;
    private final AssistantFlagsService flagsService;

    public StoreAssistantController(StoreAssistantGuard guard,
                                    StoreAssistantChatService chatService,
                                    ConversationLogger conversationLogger,
                                    CustomerRepository customerRepository,
                                    AssistantFlagsService flagsService) {
        this.guard = guard;
        this.chatService = chatService;
        this.conversationLogger = conversationLogger;
        this.customerRepository = customerRepository;
        this.flagsService = flagsService;
    }

    @PostMapping("/chat")
    public ResponseEntity<StoreChatResponse> chat(@RequestBody(required = false) StoreChatRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        // Defense-in-depth: disabling the store assistant from the admin
        // dashboard must actually block API traffic, not just hide the widget.
        if (!flagsService.isStoreEnabled()) {
            StoreChatResponse disabled = new StoreChatResponse();
            disabled.message = "Alışveriş asistanı şu anda devre dışı. Yardım için destek ekibimizle iletişime geçebilirsiniz.";
            disabled.suggestedActions = List.of();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(disabled);
        }

        StoreAssistantActor actor = guard.resolveActor(httpRequest, httpResponse);

        // Rate limit ladder — returns 200 + rateLimit payload even on deny so
        // the widget can render a friendly message inline.
        RateLimitDecision decision = guard.checkRateLimit(actor);
        if (!decision.allowed()) {
            return ResponseEntity.ok(rateLimitedResponse(decision));
        }

        // Resolve display name for customer (optional; only for prompt context).
        String displayName = null;
        if (actor.customerId() != null) {
            Optional<Customer> customer = customerRepository.findById(actor.customerId());
            if (customer.isPresent()) {
                Customer c = customer.get();
                String full = ((c.getFirstName() != null ? c.getFirstName() : "")
                        + " "
                        + (c.getLastName() != null ? c.getLastName() : "")).trim();
                displayName = full.isBlank() ? null : full;
            }
        }

        // Build context + persist / resume conversation row.
        AssistantContext context = actor.customerId() != null
                ? AssistantContext.storeCustomer(actor.customerId(), displayName)
                : AssistantContext.storeGuest(actor.guestSessionId());
        AssistantContextHolder.set(context);

        AssistantConversation conversation;
        try {
            if (actor.customerId() != null) {
                conversation = conversationLogger.resolveCustomerConversation(
                        actor.customerId(), displayName, actor.ipHash(), actor.userAgent());
            } else {
                conversation = conversationLogger.resolveGuestConversation(
                        actor.guestSessionId(), actor.ipHash(), actor.userAgent());
            }

            StoreChatResponse response = chatService.chat(request, conversation);
            return ResponseEntity.ok(response);
        } finally {
            AssistantContextHolder.clear();
        }
    }

    private StoreChatResponse rateLimitedResponse(RateLimitDecision decision) {
        StoreChatResponse resp = new StoreChatResponse();
        resp.message = guard.friendlyDenyMessage(decision);
        resp.rateLimit = new StoreChatResponse.RateLimitInfo();
        resp.rateLimit.scope = decision.scope().name();
        resp.rateLimit.retryAfterSeconds = decision.retryAfterSeconds();
        resp.rateLimit.reachedGuestLimit = decision.reachedGuestLimit();
        resp.rateLimit.friendlyMessage = resp.message;
        resp.suggestedActions = decision.reachedGuestLimit()
                ? List.of(StoreSuggestedAction.loginPrompt())
                : List.of();
        return resp;
    }
}
