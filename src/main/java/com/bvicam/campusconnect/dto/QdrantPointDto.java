package com.bvicam.campusconnect.dto;

import java.util.List;
import java.util.Map;

/**
 * Data transfer record representing a vectorized point to be indexed into Qdrant.
 *
 * @param id      unique integer point identifier (derived from DocumentChunk ID)
 * @param vector  dense floating-point embedding vector (e.g. 768-dim from Gemma 2)
 * @param payload metadata attributes (documentId, chunkIndex, title, category, chunkText, sourceUrl)
 */
public record QdrantPointDto(
        Long id,
        List<Double> vector,
        Map<String, Object> payload
) {
}
