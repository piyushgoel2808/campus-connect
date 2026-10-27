package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.SourceType;

/**
 * Citation reference linking a generated response back to an ingested notice or web page.
 */
public record CitationDto(
        Long documentId,
        String title,
        DocumentCategory category,
        SourceType sourceType,
        String sourceUrl,
        double similarity
) {
}
