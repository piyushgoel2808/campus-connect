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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private volatile String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final Duration timeout;
    private final String authHeader;
    private final String authToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile long offlineCooldownUntil = 0L;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaClientService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:gemma4:e4b}") String chatModel,
            @Value("${ollama.embedding-model:embeddinggemma-2:latest}") String embeddingModel,
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
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.authHeader = (authHeader != null && !authHeader.isBlank()) ? authHeader.trim() : "X-GradBot-Key";
        this.authToken = (authToken != null) ? authToken.trim() : "";

        // Enforce HTTPS or loopback if an authentication secret token is configured (CWE-319 protection)
        validateEndpointUri(this.baseUrl, this.authToken, "ollama");

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
        return isHealthyAt(this.baseUrl);
    }

    /**
     * Probes a candidate Ollama base URL with the configured shared-secret header and verifies
     * that both the required chat and embedding models are present.
     *
     * @param candidateBaseUrl base URL to probe (e.g. Cloudflare tunnel HTTPS URL)
     * @return true if the endpoint responds 200 OK with required models, false otherwise
     */
    public boolean isHealthyAt(String candidateBaseUrl) {
        try {
            String normalized = normalizeBaseUrl(candidateBaseUrl);
            validateEndpointUri(normalized, this.authToken, "ollama");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalized + "/api/tags"))
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

        String generated = executeChatCompletion(requestPayload, timeout, "chat generation");
        if (generated != null && !generated.isBlank()) {
            return generated;
        }

        return "⚠️ **GradBot AI Runner is currently in standby mode.**\n\n"
                + "The local high-performance Mac AI runner (`gemma4:e4b`) is offline. "
                + "You can still browse campus events, jobs, batch directory, and notices via the dashboard tabs, "
                + "or ask the system administrator to start the AI runner via `./start-ai-runner.sh`.";
    }

    /**
     * Extracts text verbatim from a Base64-encoded image (such as a scanned PDF page or exam date sheet screenshot)
     * using the configured vision-capable Ollama chat model (e.g., {@code gemma4:e4b}).
     *
     * @param base64Image Base64-encoded PNG/JPEG image payload
     * @return extracted text (preserving tables in Markdown), or empty string if blank/unreadable/offline
     */
    public String extractTextFromImage(String base64Image) {
        return extractTextFromImage(base64Image, null);
    }

    /**
     * Extracts text verbatim from a Base64-encoded image with optional page label context.
     *
     * @param base64Image Base64-encoded PNG/JPEG image payload
     * @param pageLabel   optional page context label (e.g., {@code "Page 1 of 3"})
     * @return extracted text (preserving tables in Markdown), or empty string if blank/unreadable/offline
     */
    public String extractTextFromImage(String base64Image, String pageLabel) {
        if (base64Image == null || base64Image.isBlank()) {
            return "";
        }

        String contextNote = (pageLabel != null && !pageLabel.isBlank()) ? " (" + pageLabel + ")" : "";
        String ocrPrompt = "You are a precise document OCR engine. Extract ALL readable text visible in this image"
                + contextNote
                + " verbatim, including headings, circular numbers, dates, exam timings, paper codes, subjects, table rows, and footnotes. "
                + "Preserve tabular schedules cleanly using Markdown pipe tables (| Column | Column |). "
                + "Do not summarize or add conversational commentary. If there is no readable text, reply with EMPTY.";

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", ocrPrompt,
                "images", List.of(base64Image.trim())
        );

        // Disable reasoning tokens ("think": false) so multimodal OCR completes in ~1-2s per page
        Map<String, Object> requestPayload = Map.of(
                "model", chatModel,
                "think", false,
                "messages", List.of(userMessage),
                "stream", false,
                "options", Map.of("temperature", 0.1)
        );

        Duration ocrTimeout = timeout.compareTo(Duration.ofSeconds(90)) < 0 ? Duration.ofSeconds(90) : timeout;
        String extracted = executeChatCompletion(requestPayload, ocrTimeout, "vision OCR" + contextNote);
        if (extracted == null || extracted.isBlank() || "EMPTY".equalsIgnoreCase(extracted.trim())) {
            return "";
        }
        return extracted.trim();
    }

    private String executeChatCompletion(Map<String, Object> requestPayload, Duration requestTimeout, String operationLabel) {
        if (System.currentTimeMillis() < offlineCooldownUntil) {
            return "";
        }
        try {
            String jsonBody = objectMapper.writeValueAsString(requestPayload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                offlineCooldownUntil = 0L;
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode messageNode = root.path("message").path("content");
                if (!messageNode.isMissingNode() && !messageNode.asText().isBlank()) {
                    return messageNode.asText().trim();
                }
            } else {
                log.warn("Ollama /api/chat ({}) returned HTTP {}: {}", operationLabel, response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            offlineCooldownUntil = System.currentTimeMillis() + 60_000L;
            log.warn("Ollama {} failed or timed out (runner offline): {}", operationLabel, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return "";
    }

    /**
     * Generates a vector embedding array for the given text using Ollama's {@code /api/embeddings} endpoint.
     *
     * @param text content to vectorize
     * @return list of floating-point embedding coordinates, or empty list on failure
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.isBlank() || System.currentTimeMillis() < offlineCooldownUntil) {
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
                offlineCooldownUntil = 0L;
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
            offlineCooldownUntil = System.currentTimeMillis() + 60_000L;
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

    public String getAuthHeader() {
        return authHeader;
    }

    public boolean hasAuthToken() {
        return !authToken.isEmpty();
    }

    /**
     * Verifies a candidate runner shared secret in constant time.
     */
    public boolean matchesAuthToken(String providedToken) {
        if (authToken.isEmpty() || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                authToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Atomically updates the active Ollama base URL at runtime after validating CWE-319 transport rules.
     */
    public void updateBaseUrl(String newBaseUrl) {
        String normalized = normalizeBaseUrl(newBaseUrl);
        validateEndpointUri(normalized, this.authToken, "ollama");
        this.baseUrl = normalized;
        this.offlineCooldownUntil = 0L;
        log.info("Updated live Ollama endpoint dynamically to {}", normalized);
    }

    public static String normalizeBaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL must not be blank");
        }
        String trimmed = rawUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static boolean isLoopbackHost(String host) {
        return host == null
                || "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "host.docker.internal".equalsIgnoreCase(host);
    }

    public static URI validateEndpointUri(String normalizedUrl, String authToken, String serviceLabel) {
        URI base = URI.create(normalizedUrl);
        String scheme = base.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(serviceLabel + ".base-url must use http:// or https://");
        }
        String host = base.getHost();
        if (authToken != null && !authToken.isEmpty() && !"https".equalsIgnoreCase(scheme) && !isLoopbackHost(host)) {
            throw new IllegalArgumentException(serviceLabel + ".auth-token requires an https:// or loopback " + serviceLabel + ".base-url");
        }
        return base;
    }
}
