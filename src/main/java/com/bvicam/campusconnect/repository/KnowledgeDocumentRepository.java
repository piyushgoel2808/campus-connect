package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link KnowledgeDocument} entities.
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    List<KnowledgeDocument> findByCategoryOrderByCreatedAtDesc(DocumentCategory category);

    Optional<KnowledgeDocument> findByExternalKey(String externalKey);

    Optional<KnowledgeDocument> findFirstBySourceUrl(String sourceUrl);

    boolean existsByExternalKeyAndSyncStatus(String externalKey, KnowledgeDocument.SyncStatus syncStatus);

    long countBySourceType(SourceType sourceType);

    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.uploadedBy = null WHERE d.uploadedBy.id = :userId")
    int clearUploaderByUserId(@Param("userId") Long userId);
}
