package com.warehouse.assistant.admin.web;

import com.warehouse.assistant.admin.entity.AssistantConversation;
import com.warehouse.assistant.admin.service.AssistantObservabilityService;
import com.warehouse.assistant.core.api.AssistantProfile;
import com.warehouse.assistant.core.config.AssistantFlagsService;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import com.warehouse.assistant.core.rag.AssistantRagInitService;
import com.warehouse.assistant.core.rag.ProductEmbeddingBackfillService;
import com.warehouse.assistant.core.rag.VectorSearchService;
import com.warehouse.util.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin endpoints for the assistant dashboard, conversation logs, product
 * backfill, pgvector late-init and feature flag toggles.
 */
@RestController
@RequestMapping("/api/admin/assistant")
@Profile("!test")
public class AssistantDashboardAdminController {

    private final AssistantObservabilityService observabilityService;
    private final ProductEmbeddingBackfillService productBackfill;
    private final AssistantRagInitService ragInitService;
    private final VectorSearchService vectorSearchService;
    private final AssistantFlagsService flagsService;
    private final AssistantRuntimeConfig runtimeConfig;

    public AssistantDashboardAdminController(AssistantObservabilityService observabilityService,
                                              ProductEmbeddingBackfillService productBackfill,
                                              AssistantRagInitService ragInitService,
                                              VectorSearchService vectorSearchService,
                                              AssistantFlagsService flagsService,
                                              AssistantRuntimeConfig runtimeConfig) {
        this.observabilityService = observabilityService;
        this.productBackfill = productBackfill;
        this.ragInitService = ragInitService;
        this.vectorSearchService = vectorSearchService;
        this.flagsService = flagsService;
        this.runtimeConfig = runtimeConfig;
    }

    // ── Runtime config (AI Settings page) ──

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(runtimeConfig.getAllForAdmin());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        runtimeConfig.updateFromAdmin(body, CurrentUser.usernameOrSystem());
        return ResponseEntity.ok(runtimeConfig.getAllForAdmin());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> body = new HashMap<>(observabilityService.getDashboardKpis());
        body.put("ragAvailable", vectorSearchService.isRagAvailable());
        body.put("flags", flagsService.getAllFlags());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/conversations")
    public ResponseEntity<Page<AssistantConversation>> conversations(
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "25") int size) {
        AssistantProfile parsed = null;
        if (profile != null && !profile.isBlank()) {
            try { parsed = AssistantProfile.valueOf(profile.toUpperCase()); } catch (Exception ignored) {}
        }
        java.time.LocalDateTime start = parseDateParam(startDate);
        java.time.LocalDateTime end = parseDateParam(endDate);
        // If endDate is date-only (no time), set to end of day
        if (end != null && endDate != null && !endDate.contains("T")) {
            end = end.toLocalDate().atTime(23, 59, 59);
        }
        return ResponseEntity.ok(observabilityService.listConversations(
                parsed, start, end, search,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "startedAt"))));
    }

    private java.time.LocalDateTime parseDateParam(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return java.time.LocalDateTime.parse(raw); } catch (Exception e1) {
            try { return java.time.LocalDate.parse(raw).atStartOfDay(); } catch (Exception e2) {
                return null;
            }
        }
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Map<String, Object>> conversationDetail(@PathVariable Long id) {
        Map<String, Object> detail = observabilityService.getConversationDetail(id);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    @PostMapping("/products/reindex")
    public ResponseEntity<Map<String, Object>> reindexProducts() {
        int processed = productBackfill.backfillAll();
        Map<String, Object> body = new HashMap<>();
        body.put("processed", processed);
        body.put("ragAvailable", vectorSearchService.isRagAvailable());
        return ResponseEntity.ok(body);
    }

    /**
     * Late-init for pgvector schema. Use this when the app was first booted
     * against a Postgres without pgvector and you've since enabled the
     * extension on your managed Postgres service. Creates the missing
     * vector tables and indexes idempotently, then re-detects availability
     * without requiring an app restart.
     */
    @PostMapping("/rag/init")
    public ResponseEntity<AssistantRagInitService.InitResult> initRagSchema() {
        return ResponseEntity.ok(ragInitService.initializeRagSchema());
    }

    @GetMapping("/rag/status")
    public ResponseEntity<Map<String, Object>> ragStatus() {
        Map<String, Object> body = new HashMap<>();
        body.put("ragAvailable", vectorSearchService.isRagAvailable());
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Feature flags (WMS assistant on/off, Store assistant on/off)
    // -------------------------------------------------------------------------

    public static class FlagsUpdateRequest {
        public Boolean wmsEnabled;
        public Boolean storeEnabled;
    }

    @GetMapping("/flags")
    public ResponseEntity<Map<String, Boolean>> getFlags() {
        return ResponseEntity.ok(flagsService.getAllFlags());
    }

    @PostMapping("/flags")
    public ResponseEntity<Map<String, Boolean>> updateFlags(@RequestBody FlagsUpdateRequest body) {
        flagsService.updateFlags(
                body != null ? body.wmsEnabled : null,
                body != null ? body.storeEnabled : null,
                CurrentUser.usernameOrSystem());
        return ResponseEntity.ok(flagsService.getAllFlags());
    }
}
