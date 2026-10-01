package com.aisupporthub.repository;

import com.aisupporthub.model.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
