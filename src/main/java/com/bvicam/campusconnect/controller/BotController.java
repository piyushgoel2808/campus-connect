package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.service.CampusAiService;
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

    private void enforceRateLimit(String userKey) {
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
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(429),
                        "Too many GradBot requests. Please wait a minute before trying again."
                );
            }
            timestamps.addLast(now);
        }
    }
}
