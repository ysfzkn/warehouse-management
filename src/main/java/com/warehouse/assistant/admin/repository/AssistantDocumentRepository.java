package com.warehouse.assistant.admin.repository;

import com.warehouse.assistant.admin.entity.AssistantDocument;
import com.warehouse.assistant.admin.entity.AssistantDocumentScope;
import com.warehouse.assistant.admin.entity.AssistantDocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistantDocumentRepository extends JpaRepository<AssistantDocument, Long> {

    List<AssistantDocument> findByStatusOrderByCreatedAtAsc(AssistantDocumentStatus status);

    List<AssistantDocument> findByScopeInAndStatus(List<AssistantDocumentScope> scopes, AssistantDocumentStatus status);

    Page<AssistantDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
