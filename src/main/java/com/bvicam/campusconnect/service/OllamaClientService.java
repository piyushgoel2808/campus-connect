package com.bvicam.campusconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service client communicating with the local Ollama runtime daemon.
 * <p>
 * Performs chat generation using {@code gemma4:e4b} and text vectorization
 * using {@code nomic-embed-text}. Includes resilient fallback handling if the
 * Ollama daemon is offline or experiencing heavy load.
 * </p>
 */
@Service
public class OllamaClientService {

    private static final Logger log = LoggerFactory.getLogger(OllamaClientService.class);

    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaClientService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:gemma4:e4b}") String chatModel,
            @Value("${ollama.embedding-model:nomic-embed-text}") String embeddingModel,
            @Value("${ollama.timeout-seconds:60}") int timeoutSeconds) {
        this(baseUrl, chatModel, embeddingModel, timeoutSeconds, new ObjectMapper());
    }

    public OllamaClientService(
            String baseUrl,
            String chatModel,
            String embeddingModel,
            int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Generates a conversational response from Gemma 4 via Ollama's {@code /api/chat} endpoint.
     *
     * @param systemPrompt behavioral prompt, persona instructions, and injected context
     * @param userQuery    the user's natural language question
     * @return the generated markdown response, or a fallback message if Ollama is unreachable
     */
    public String generateChatResponse(String systemPrompt, String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return "Please provide a question for GradBot.";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userQuery));

        Map<String, Object> requestPayload = Map.of(
                "model", chatModel,
                "messages", messages,
                "stream", false,
                "options", Map.of("temperature", 0.3)
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(requestPayload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode messageNode = root.path("message").path("content");
                if (!messageNode.isMissingNode() && !messageNode.asText().isBlank()) {
                    return messageNode.asText().trim();
                }
            } else {
                log.warn("Ollama /api/chat returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Ollama chat generation failed or timed out: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return "⚠️ GradBot AI is currently operating in offline mode. For academic inquiries, please consult the administration office at admin@bvicam.in.";
    }

    /**
     * Generates a vector embedding array for the given text using Ollama's {@code /api/embeddings} endpoint.
     *
     * @param text content to vectorize
     * @return list of floating-point embedding coordinates, or empty list on failure
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, Object> requestPayload = Map.of(
                "model", embeddingModel,
                "prompt", text
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(requestPayload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.path("embedding");
                if (embeddingNode.isArray()) {
                    List<Double> vector = new ArrayList<>(embeddingNode.size());
                    for (JsonNode val : embeddingNode) {
                        vector.add(val.asDouble());
                    }
                    return vector;
                }
            } else {
                log.warn("Ollama /api/embeddings returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Ollama embedding generation failed: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return Collections.emptyList();
    }

    public String getChatModel() {
        return chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}
