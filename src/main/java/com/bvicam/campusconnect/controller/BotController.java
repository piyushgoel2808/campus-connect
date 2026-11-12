package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.service.CampusAiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST Controller for the interactive GradBot AI Campus Assistant ({@code /api/bot}).
 */
@RestController
@RequestMapping("/api/bot")
public class BotController {

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final int MAX_SYNC_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final CampusAiService campusAiService;
    private final ConcurrentHashMap<String, Deque<Long>> rateLimitWindows = new ConcurrentHashMap<>();

    /**
     * Constructor injection adhering to Spring Boot engineering best practices.
     *
     * @param campusAiService core AI and RAG orchestration service
     */
    public BotController(CampusAiService campusAiService) {
        this.campusAiService = campusAiService;
    }

    /**
     * Processes a user question and returns a structured AI-generated response from GradBot.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/bot/ask}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> JSON object containing {@code {"question": "..."}}</li>
     *   <li><b>Response:</b> JSON object containing {@code {"answer": "...", "model": "...", "citations": [...]}}</li>
     * </ul>
     *
     * @param payload map containing the user's {@code "question"} string
     * @return structured response containing answer, model name, and citations
     */
    @PostMapping("/ask")
    public BotAskResponse askBot(@RequestBody(required = false) Map<String, String> payload,
                                 Principal principal) {
        if (principal != null && principal.getName() != null) {
            enforceRateLimit(principal.getName());
        }
        String rawQuestion = null;
        if (payload != null) {
            rawQuestion = payload.containsKey("question") ? payload.get("question") : payload.get("message");
        }
        return campusAiService.answerQuestion(rawQuestion);
    }

    /**
     * Reports real-time connectivity status of the AI runner (Ollama daemon / Cloudflare tunnel).
     */
    @GetMapping("/health")
    public Map<String, Object> checkHealth() {
        return campusAiService.getHealthStatus();
    }

    /**
     * Shared-secret webhook enabling the local AI runner to hot-reload its active Cloudflare
     * tunnel URL in-memory with zero standby delay before/alongside Render persistence.
     */
    @PostMapping("/runner-endpoint")
    public Map<String, Object> updateRunnerEndpoint(@RequestBody(required = false) Map<String, String> payload,
                                                    HttpServletRequest request) {
        enforceRateLimit(
                "__runner_sync__",
                MAX_SYNC_REQUESTS_PER_MINUTE,
                "Too many runner endpoint sync attempts. Please wait a minute."
        );
        String authHeaderName = campusAiService.getRunnerAuthHeader();
        if (authHeaderName == null || authHeaderName.isBlank()) {
            authHeaderName = "X-GradBot-Key";
        }
        String providedSecret = request != null ? request.getHeader(authHeaderName) : null;
        String baseUrl = payload != null ? payload.get("baseUrl") : null;
        return campusAiService.updateRunnerEndpoint(providedSecret, baseUrl);
    }

    private void enforceRateLimit(String userKey) {
        enforceRateLimit(
                userKey,
                MAX_REQUESTS_PER_MINUTE,
                "Too many GradBot requests. Please wait a minute before trying again."
        );
    }

    private void enforceRateLimit(String userKey, int maxRequests, String message) {
        long now = System.currentTimeMillis();
        if (rateLimitWindows.size() > 1000) {
            rateLimitWindows.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    while (!e.getValue().isEmpty() && now - e.getValue().peekFirst() > WINDOW_MILLIS) {
                        e.getValue().pollFirst();
                    }
                    return e.getValue().isEmpty();
                }
            });
        }
        Deque<Long> timestamps = rateLimitWindows.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(429),
                        message
                );
            }
            timestamps.addLast(now);
        }
    }
}
