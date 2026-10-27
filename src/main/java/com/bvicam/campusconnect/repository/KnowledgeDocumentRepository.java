package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link KnowledgeDocument} entities.
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    List<KnowledgeDocument> findByCategoryOrderByCreatedAtDesc(DocumentCategory category);
}
