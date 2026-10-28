package com.bvicam.campusconnect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OllamaClientService}.
 * Verifies defensive handling of blank prompts, auth header configuration, and offline daemon fallback.
 */
class OllamaClientServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("generateChatResponse with blank query returns helpful prompt without network call")
    void generateChatResponse_blankQuery_returnsPrompt() {
        OllamaClientService service = new OllamaClientService(
                "http://localhost:11434",
                "gemma4:e4b",
                "embeddinggemma-2:latest",
                5,
                objectMapper
        );

        String response = service.generateChatResponse("System", "   ");
        assertThat(response).isEqualTo("Please provide a question for GradBot.");
    }

    @Test
    @DisplayName("generateChatResponse when Ollama is offline returns graceful fallback string")
    void generateChatResponse_offlineOllama_returnsFallbackMessage() {
        // Use RFC 5737 TEST-NET-1 documentation IP (unroutable) to guarantee deterministic connection failure
        OllamaClientService service = new OllamaClientService(
                "http://192.0.2.1:80",
                "gemma4:e4b",
                "embeddinggemma-2:latest",
                1,
                objectMapper
        );

        String response = service.generateChatResponse("System", "Hello");
        assertThat(response).contains("standby mode");
    }

    @Test
    @DisplayName("generateEmbedding with blank text returns empty list")
    void generateEmbedding_blankText_returnsEmptyList() {
        OllamaClientService service = new OllamaClientService(
                "http://localhost:11434",
                "gemma4:e4b",
                "embeddinggemma-2:latest",
                5,
                objectMapper
        );

        List<Double> embedding = service.generateEmbedding("   ");
        assertThat(embedding).isEmpty();
    }

    @Test
    @DisplayName("isHealthy returns false when base URL daemon is unreachable")
    void isHealthy_offlineDaemon_returnsFalse() {
        OllamaClientService service = new OllamaClientService(
                "https://192.0.2.1:443",
                "gemma4:e4b",
                "embeddinggemma-2:latest",
                1,
                "X-GradBot-Key",
                "secret-token-123",
                objectMapper
        );

        assertThat(service.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("constructor rejects non-loopback insecure HTTP URL when auth token is set")
    void constructor_insecureHttpWithToken_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaClientService(
                        "http://example.com:11434",
                        "gemma4:e4b",
                        "embeddinggemma-2:latest",
                        1,
                        "X-GradBot-Key",
                        "my-secret-token",
                        objectMapper
                )
        );
    }
}
