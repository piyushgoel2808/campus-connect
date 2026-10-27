package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DocumentChunk} text segments and vector embeddings.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document d WHERE d.category = :category ORDER BY c.id ASC")
    List<DocumentChunk> findByDocumentCategory(@Param("category") DocumentCategory category);

    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document d ORDER BY c.id ASC")
    List<DocumentChunk> findAllWithDocument();
}
