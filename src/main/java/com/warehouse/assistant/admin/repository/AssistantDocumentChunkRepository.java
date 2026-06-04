package com.warehouse.assistant.admin.repository;

import com.warehouse.assistant.admin.entity.AssistantDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantDocumentChunkRepository extends JpaRepository<AssistantDocumentChunk, Long> {

    @Modifying
    @Query("delete from AssistantDocumentChunk c where c.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);
}
