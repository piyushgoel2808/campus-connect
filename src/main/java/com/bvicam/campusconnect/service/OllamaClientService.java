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
 * Service client communicating with the Ollama runtime daemon.
 * <p>
 * Performs chat generation using {@code gemma4:e4b} and text vectorization
 * using {@code nomic-embed-text}. Supports both direct localhost access and secure
 * Cloudflare tunnel connections (Mac to Render) with shared secret authentication.
 * Includes resilient fallback handling if the runner is offline.
 * </p>
 */
@Service
public class OllamaClientService {

    private static final Logger log = LoggerFactory.getLogger(OllamaClientService.class);

    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final Duration timeout;
    private final String authHeader;
    private final String authToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaClientService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:gemma4:e4b}") String chatModel,
            @Value("${ollama.embedding-model:nomic-embed-text}") String embeddingModel,
            @Value("${ollama.timeout-seconds:60}") int timeoutSeconds,
            @Value("${ollama.auth-header:X-GradBot-Key}") String authHeader,
            @Value("${ollama.auth-token:}") String authToken) {
        this(baseUrl, chatModel, embeddingModel, timeoutSeconds, authHeader, authToken, new ObjectMapper());
    }

    public OllamaClientService(
            String baseUrl,
            String chatModel,
            String embeddingModel,
            int timeoutSeconds,
            ObjectMapper objectMapper) {
        this(baseUrl, chatModel, embeddingModel, timeoutSeconds, "X-GradBot-Key", "", objectMapper);
    }

    public OllamaClientService(
            String baseUrl,
            String chatModel,
            String embeddingModel,
            int timeoutSeconds,
            String authHeader,
            String authToken,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.authHeader = (authHeader != null && !authHeader.isBlank()) ? authHeader.trim() : "X-GradBot-Key";
        this.authToken = (authToken != null) ? authToken.trim() : "";

        // Enforce HTTPS or loopback if an authentication secret token is configured (CWE-319 protection)
        URI base = URI.create(this.baseUrl);
        String host = base.getHost();
        boolean loopback = host == null || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "host.docker.internal".equalsIgnoreCase(host);
        if (!this.authToken.isEmpty() && !"https".equalsIgnoreCase(base.getScheme()) && !loopback) {
            throw new IllegalArgumentException("ollama.auth-token requires an https:// or loopback ollama.base-url");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Checks if the configured Ollama base URL is reachable and the required models are available.
     *
     * @return true if Ollama daemon reports healthy status with required models, false otherwise
     */
    public boolean isHealthy() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET();
            applyAuth(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode modelsNode = root.path("models");
                if (modelsNode.isArray()) {
                    boolean hasChat = false;
                    boolean hasEmbed = false;

                    for (JsonNode m : modelsNode) {
                        String name = m.path("name").asText("");
                        if (matchesModel(name, chatModel)) {
                            hasChat = true;
                        }
                        if (matchesModel(name, embeddingModel)) {
                            hasEmbed = true;
                        }
                    }
                    return hasChat && hasEmbed;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesModel(String candidate, String configured) {
        if (candidate == null || candidate.isBlank() || configured == null || configured.isBlank()) {
            return false;
        }
        String cleanCandidate = candidate.trim();
        String cleanConfigured = configured.trim();

        if (cleanCandidate.equalsIgnoreCase(cleanConfigured)) {
            return true;
        }

        // If configuration specifies an explicit tag (e.g., "gemma4:e4b"), require exact match
        if (cleanConfigured.contains(":")) {
            return false;
        }

        // Configuration omits tag (e.g., "gemma4"): match base name or candidate with :latest
        String candidateBase = cleanCandidate.contains(":") ? cleanCandidate.split(":")[0] : cleanCandidate;
        return candidateBase.equalsIgnoreCase(cleanConfigured)
                || cleanCandidate.equalsIgnoreCase(cleanConfigured + ":latest");
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
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

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
            log.warn("Ollama chat generation failed or timed out (runner offline): {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return "⚠️ **GradBot AI Runner is currently in standby mode.**\n\n"
                + "The local high-performance Mac AI runner (`gemma4:e4b`) is offline. "
                + "You can still browse campus events, jobs, batch directory, and notices via the dashboard tabs, "
                + "or ask the system administrator to start the AI runner via `./start-ai-runner.sh`.";
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
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

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
            log.warn("Ollama embedding generation failed (runner offline): {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Applies configured shared-secret authentication header if present.
     */
    private void applyAuth(HttpRequest.Builder builder) {
        if (!authToken.isEmpty()) {
            builder.header(authHeader, authToken);
        }
    }

    public String getChatModel() {
        return chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
