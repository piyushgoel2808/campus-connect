package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.service.CampusAiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller for the interactive GradBot AI Campus Assistant ({@code /api/bot}).
 * <p>
 * Powers conversational Q&A for BVICAM students, alumni, and administrators,
 * backed by local Ollama (Gemma 4) and live RAG campus intelligence.
 * </p>
 */
@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final CampusAiService campusAiService;

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
    public BotAskResponse askBot(@RequestBody(required = false) Map<String, String> payload) {
        String rawQuestion = payload != null ? payload.get("question") : null;
        return campusAiService.answerQuestion(rawQuestion);
    }
}
