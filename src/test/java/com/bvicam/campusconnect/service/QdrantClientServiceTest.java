package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.QdrantPointDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link QdrantClientService}.
 * Verifies defensive handling of unroutable hosts, empty inputs, and CWE-319 HTTPS enforcement.
 */
class QdrantClientServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("isHealthy returns false when Qdrant host is unreachable")
    void isHealthy_unreachableHost_returnsFalse() {
        QdrantClientService service = new QdrantClientService(
                "http://127.0.0.1:65530",
                "campus_knowledge",
                "X-GradBot-Key",
                "test-secret",
                1,
                objectMapper
        );

        assertThat(service.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("search with null or empty query vector returns empty list")
    void search_emptyVector_returnsEmptyList() {
        QdrantClientService service = new QdrantClientService(
                "http://localhost:6333",
                "campus_knowledge",
                objectMapper
        );

        assertThat(service.search(null, 5, 0.25)).isEmpty();
        assertThat(service.search(Collections.emptyList(), 5, 0.25)).isEmpty();
    }

    @Test
    @DisplayName("upsertPoints with empty list returns true without making network call")
    void upsertPoints_emptyList_returnsTrue() {
        QdrantClientService service = new QdrantClientService(
                "http://localhost:6333",
                "campus_knowledge",
                objectMapper
        );

        assertThat(service.upsertPoints(Collections.emptyList())).isTrue();
    }

    @Test
    @DisplayName("deleteByDocumentId with null document ID returns true")
    void deleteByDocumentId_nullId_returnsTrue() {
        QdrantClientService service = new QdrantClientService(
                "http://localhost:6333",
                "campus_knowledge",
                objectMapper
        );

        assertThat(service.deleteByDocumentId(null)).isTrue();
    }

    @Test
    @DisplayName("constructor rejects non-loopback insecure HTTP URL when auth token is configured")
    void constructor_insecureHttpWithToken_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QdrantClientService(
                        "http://external-qdrant.example.com:6333",
                        "campus_knowledge",
                        "X-GradBot-Key",
                        "secret-key-xyz",
                        1,
                        objectMapper
                )
        );
    }
}
