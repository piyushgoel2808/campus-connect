package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing an ingested knowledge document metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDto {

    private Long id;
    private String title;
    private DocumentCategory category;
    private SourceType sourceType;
    private String sourceUrl;
    private String originalFileName;
    private int chunkCount;
    private LocalDateTime createdAt;

    /**
     * Factory method mapping a JPA entity to a projection DTO.
     *
     * @param doc source entity
     * @return transformed DTO
     */
    public static KnowledgeDocumentDto fromEntity(KnowledgeDocument doc) {
        if (doc == null) {
            return null;
        }
        return new KnowledgeDocumentDto(
                doc.getId(),
                doc.getTitle(),
                doc.getCategory(),
                doc.getSourceType(),
                doc.getSourceUrl(),
                doc.getOriginalFileName(),
                doc.getChunkCount(),
                doc.getCreatedAt()
        );
    }
}
