package com.bvicam.campusconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service client for Groq Cloud API (OpenAI-compatible /v1/chat/completions).
 * Provides ultra-fast cloud LLM inference and multimodal vision OCR without requiring a local laptop runner.
 */
@Service
public class GroqClientService {

    private static final Logger log = LoggerFactory.getLogger(GroqClientService.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GroqClientService(
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:qwen/qwen3.8-27b}") String model,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.timeout-seconds:30}") int timeoutSeconds) {
        this(apiKey, model, baseUrl, timeoutSeconds, new ObjectMapper());
    }

    public GroqClientService(
            String apiKey,
            String model,
            String baseUrl,
            int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "qwen/qwen3.8-27b";
        String normalizedBase = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.groq.com/openai/v1";
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        this.baseUrl = normalizedBase;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    /**
     * Checks if Groq API key is present and configured.
     */
    public boolean isConfigured() {
        return !this.apiKey.isEmpty();
    }

    public String getModel() {
        return this.model;
    }

    /**
     * Quick health check verifying connectivity and API key validity with Groq.
     */
    public boolean isHealthy() {
        if (!isConfigured()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "CampusConnect/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Groq health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates a chat response using Groq Cloud chat completions.
     *
     * @param systemPrompt System instructions and context
     * @param userMessage  User query
     * @return Generated text response or null on error
     */
    public String generateChatResponse(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            return null;
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", (userMessage != null) ? userMessage : ""));

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.3);

            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "CampusConnect/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).path("message");
                    String content = messageNode.path("content").asText("");
                    if (!content.isBlank()) {
                        return content.trim();
                    }
                }
            } else {
                log.error("Groq API returned HTTP {} with body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to generate chat response from Groq: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extracts text from an image (Vision OCR) using Groq multimodal model (e.g. qwen/qwen3.8-27b).
     *
     * @param base64Image Clean Base64 PNG/JPEG image string
     * @param prompt      Extraction instruction prompt
     * @return Transcribed markdown text or empty string on failure
     */
    public String extractTextFromImage(String base64Image, String prompt) {
        if (!isConfigured() || base64Image == null || base64Image.isBlank()) {
            return "";
        }
        try {
            String instruction = (prompt != null && !prompt.isBlank())
                    ? prompt
                    : "Extract all text, notices, dates, tables, and details from this image accurately in clean Markdown.";

            String dataUrl = base64Image.startsWith("data:")
                    ? base64Image
                    : "data:image/png;base64," + base64Image;

            List<Map<String, Object>> contentParts = List.of(
                    Map.of("type", "text", "text", instruction),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
            );

            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", contentParts
            );

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(message));
            body.put("temperature", 0.1);

            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "CampusConnect/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0).path("message").path("content").asText("");
                    return content.trim();
                }
            } else {
                log.warn("Groq Vision OCR returned HTTP {} with body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Groq Vision OCR call failed: {}", e.getMessage());
        }
        return "";
    }
}
