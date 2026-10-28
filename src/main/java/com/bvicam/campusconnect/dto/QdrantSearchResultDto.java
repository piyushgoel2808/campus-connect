package com.bvicam.campusconnect.dto;

import java.util.Map;

/**
 * Data transfer record representing a nearest-neighbor vector search hit from Qdrant.
 *
 * @param id      point identifier
 * @param score   cosine similarity score (0.0 to 1.0)
 * @param payload metadata payload containing chunk text, document title, and category
 */
public record QdrantSearchResultDto(
        Long id,
        Double score,
        Map<String, Object> payload
) {
}
