package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.QdrantPointDto;
import com.bvicam.campusconnect.dto.QdrantSearchResultDto;
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
import java.util.*;

/**
 * Service client communicating with Qdrant Vector Database REST API (port 6333 / tunnel /qdrant).
 * Provides collection lifecycle, point upsert, filtered deletion, and HNSW vector search.
 */
@Service
public class QdrantClientService {

    private static final Logger log = LoggerFactory.getLogger(QdrantClientService.class);

    private final String baseUrl;
    private final String collectionName;
    private final String authHeader;
    private final String authToken;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public QdrantClientService(
            @Value("${qdrant.base-url:${ollama.base-url}/qdrant}") String baseUrl,
            @Value("${qdrant.collection-name:campus_knowledge}") String collectionName,
            @Value("${qdrant.auth-header:X-GradBot-Key}") String authHeader,
            @Value("${qdrant.auth-token:}") String authToken,
            @Value("${qdrant.timeout-seconds:10}") int timeoutSeconds) {
        this(baseUrl, collectionName, authHeader, authToken, timeoutSeconds, new ObjectMapper());
    }

    public QdrantClientService(
            String baseUrl,
            String collectionName,
            String authHeader,
            String authToken,
            int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.collectionName = (collectionName != null && !collectionName.isBlank()) ? collectionName.trim() : "campus_knowledge";
        this.authHeader = (authHeader != null && !authHeader.isBlank()) ? authHeader.trim() : "X-GradBot-Key";
        this.authToken = (authToken != null) ? authToken.trim() : "";
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));

        // Enforce HTTPS or loopback if an authentication secret token is configured (CWE-319 protection)
        URI base = URI.create(this.baseUrl);
        String host = base.getHost();
        boolean loopback = host == null || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "host.docker.internal".equalsIgnoreCase(host);
        if (!this.authToken.isEmpty() && !"https".equalsIgnoreCase(base.getScheme()) && !loopback) {
            throw new IllegalArgumentException("qdrant.auth-token requires an https:// or loopback qdrant.base-url");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public QdrantClientService(String baseUrl, String collectionName, ObjectMapper objectMapper) {
        this(baseUrl, collectionName, "X-GradBot-Key", "", 10, objectMapper);
    }

    /**
     * Checks if Qdrant vector database is reachable and ready.
     *
     * @return true if Qdrant reports healthy status, false otherwise
     */
    public boolean isHealthy() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/readyz"))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            applyAuth(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }

            // Fallback check on collection list or root
            HttpRequest.Builder rootBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections"))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            applyAuth(rootBuilder);
            HttpResponse<String> rootResp = httpClient.send(rootBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return rootResp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensures the configured collection exists with the specified vector dimensions and Cosine distance.
     *
     * @param vectorSize dimensions of embedding vector (e.g. 768 for Gemma 2)
     * @return true if collection is ready, false on failure
     */
    public boolean ensureCollection(int vectorSize) {
        try {
            // Check if collection already exists
            HttpRequest.Builder getReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName))
                    .timeout(timeout)
                    .GET();
            applyAuth(getReq);
            HttpResponse<String> getResp = httpClient.send(getReq.build(), HttpResponse.BodyHandlers.ofString());
            if (getResp.statusCode() == 200) {
                return true;
            }

            // Create collection with Cosine distance
            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", vectorSize,
                            "distance", "Cosine"
                    )
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest.Builder putReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json));
            applyAuth(putReq);

            HttpResponse<String> putResp = httpClient.send(putReq.build(), HttpResponse.BodyHandlers.ofString());
            if (putResp.statusCode() == 200 || putResp.statusCode() == 201) {
                log.info("Created Qdrant vector collection '{}' (dim={})", collectionName, vectorSize);
                return true;
            }
            log.warn("Qdrant create collection returned HTTP {}: {}", putResp.statusCode(), putResp.body());
            return false;
        } catch (Exception e) {
            log.warn("Failed to ensure Qdrant collection '{}': {}", collectionName, e.getMessage());
            return false;
        }
    }

    /**
     * Upserts a list of vectorized points into Qdrant.
     *
     * @param points list of point records containing id, vector, and payload
     * @return true if successfully upserted, false otherwise
     */
    public boolean upsertPoints(List<QdrantPointDto> points) {
        if (points == null || points.isEmpty()) {
            return true;
        }

        try {
            Map<String, Object> body = Map.of("points", points);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName + "/points?wait=true"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            log.warn("Qdrant upsert points returned HTTP {}: {}", response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("Qdrant upsert points failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Searches for nearest neighbor points in Qdrant using vector cosine similarity.
     *
     * @param queryVector    embedding vector of the question
     * @param limit          maximum hits to return
     * @param scoreThreshold minimum cosine similarity score
     * @return list of nearest neighbor search results
     */
    public List<QdrantSearchResultDto> search(List<Double> queryVector, int limit, double scoreThreshold) {
        if (queryVector == null || queryVector.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> body = Map.of(
                    "vector", queryVector,
                    "limit", Math.max(1, limit),
                    "with_payload", true,
                    "score_threshold", scoreThreshold
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName + "/points/search"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode resultNode = root.path("result");
                if (resultNode.isArray()) {
                    List<QdrantSearchResultDto> results = new ArrayList<>(resultNode.size());
                    for (JsonNode item : resultNode) {
                        Long id = item.path("id").isNumber() ? item.path("id").asLong() : null;
                        Double score = item.path("score").asDouble();
                        Map<String, Object> payload = objectMapper.convertValue(item.path("payload"), Map.class);
                        results.add(new QdrantSearchResultDto(id, score, payload != null ? payload : Collections.emptyMap()));
                    }
                    return results;
                }
            } else {
                log.warn("Qdrant search returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Qdrant search failed: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Deletes all points associated with a specific knowledge document.
     *
     * @param documentId document ID to purge
     * @return true if deleted, false on error
     */
    public boolean deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return true;
        }

        try {
            Map<String, Object> body = Map.of(
                    "filter", Map.of(
                            "must", List.of(
                                    Map.of("key", "document_id", "match", Map.of("value", documentId))
                            )
                    )
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName + "/points/delete"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Qdrant delete by documentId failed: {}", e.getMessage());
            return false;
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (!authToken.isEmpty()) {
            builder.header(authHeader, authToken);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCollectionName() {
        return collectionName;
    }
}
