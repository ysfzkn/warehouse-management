package com.warehouse.assistant.admin.web;

import com.warehouse.assistant.admin.entity.AssistantDocument;
import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.admin.repository.AssistantDocumentRepository;
import com.warehouse.assistant.admin.service.AssistantDocumentService;
import com.warehouse.util.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Admin REST for managing assistant reference documents (upload, list,
 * re-index, delete). Secured under the admin filter chain via
 * {@code /api/admin/assistant/documents/**} in ApiPaths.
 */
@RestController
@RequestMapping("/api/admin/assistant/documents")
@Profile("!test")
public class AssistantDocumentAdminController {

    private final AssistantDocumentService documentService;
    private final AssistantDocumentRepository documentRepository;

    public AssistantDocumentAdminController(AssistantDocumentService documentService,
                                            AssistantDocumentRepository documentRepository) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
    }

    @PostMapping
    public ResponseEntity<AssistantDocument> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "scope", required = false, defaultValue = "STORE") String scope) throws IOException {

        AssistantDocumentScope parsedScope;
        try {
            parsedScope = AssistantDocumentScope.valueOf(scope.toUpperCase());
        } catch (Exception e) {
            parsedScope = AssistantDocumentScope.STORE;
        }
        AssistantDocument doc = documentService.uploadAndQueue(file, title, parsedScope, CurrentUser.usernameOrSystem());
        return ResponseEntity.ok(doc);
    }

    @GetMapping
    public ResponseEntity<List<AssistantDocument>> list(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        Page<AssistantDocument> slice = documentRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)));
        return ResponseEntity.ok(slice.getContent());
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<Void> reindex(@PathVariable Long id) {
        documentService.reindex(id);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
