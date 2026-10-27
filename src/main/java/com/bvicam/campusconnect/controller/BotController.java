package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.OllamaClientService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller for the interactive GradBot AI Campus Assistant ({@code /api/bot}).
 * <p>
 * Powers conversational Q&A for BVICAM students, alumni, and administrators,
 * backed by local Ollama (Gemma 4) and live campus data.
 * </p>
 */
@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final OllamaClientService ollamaClientService;

    /**
     * Constructor injection adhering to Spring Boot engineering best practices.
     */
    public BotController(UserRepository userRepository,
                         EventRepository eventRepository,
                         OllamaClientService ollamaClientService) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.ollamaClientService = ollamaClientService;
    }

    /**
     * Processes a user question and returns an AI generated response from Gemma 4.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/bot/ask}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> JSON object containing {@code {"question": "..."}}</li>
     *   <li><b>Response:</b> JSON object containing {@code {"answer": "...", "model": "gemma4:e4b"}}</li>
     * </ul>
     *
     * @param payload map containing the user's {@code "question"} string
     * @return map with keys {@code "answer"} and {@code "model"}
     */
    @PostMapping("/ask")
    public Map<String, String> askBot(@RequestBody(required = false) Map<String, String> payload) {
        String rawQuestion = payload != null ? payload.get("question") : null;
        if (rawQuestion == null || rawQuestion.trim().isEmpty()) {
            return Map.of(
                    "answer", "Please ask a question about BVICAM courses, placements, events, or batchmates.",
                    "model", "gemma4:e4b"
            );
        }

        String question = rawQuestion.trim();
        String lowerQuestion = question.toLowerCase();

        // Assemble bounded live campus data context from database for real-time awareness
        StringBuilder liveContext = new StringBuilder();
        try {
            // 1. Fetch upcoming events when question pertains to campus events or schedules
            if (lowerQuestion.contains("event") || lowerQuestion.contains("workshop") ||
                    lowerQuestion.contains("seminar") || lowerQuestion.contains("schedule") ||
                    lowerQuestion.contains("happening") || lowerQuestion.contains("fest")) {
                var upcomingEvents = eventRepository.findTop5ByDateTimeAfterOrderByDateTimeAsc(java.time.LocalDateTime.now());
                if (upcomingEvents != null && !upcomingEvents.isEmpty()) {
                    liveContext.append("\n[LIVE UPCOMING EVENTS IN DATABASE]\n");
                    for (var e : upcomingEvents) {
                        liveContext.append("• ").append(e.getTitle()).append(" at ").append(e.getLocation())
                                .append(" on ").append(e.getDateTime()).append("\n");
                    }
                }
            }

            // 2. Dynamic batch lookup when user inquires about batch members
            if (lowerQuestion.contains("batch") || lowerQuestion.contains("class of")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b").matcher(question);
                if (matcher.find()) {
                    int year = Integer.parseInt(matcher.group(1));
                    var batchMembers = userRepository.findAll().stream()
                            .filter(u -> u.getBatchYear() != null && u.getBatchYear() == year)
                            .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                            .limit(20)
                            .toList();
                    if (!batchMembers.isEmpty()) {
                        liveContext.append("\n[STUDENTS ENROLLED IN BATCH ").append(year).append("]\n")
                                .append(String.join(", ", batchMembers)).append("\n");
                    }
                }
            }

            // 3. Official placement highlights context
            if (lowerQuestion.contains("placement") || lowerQuestion.contains("package") || lowerQuestion.contains("recruit")) {
                liveContext.append("\n[BVICAM OFFICIAL PLACEMENT HIGHLIGHTS]\n")
                        .append("• Highest Package: 12 LPA (Microsoft)\n")
                        .append("• Average Package: 6.5 LPA\n")
                        .append("• Prominent Recruiters: TCS, Nagarro, Knoldus, Microsoft, BVICAM Research Lab\n");
            }
        } catch (Exception ignored) {
            // Non-blocking: live context injection degrades gracefully if database is undergoing migrations
        }

        String systemPrompt = """
                You are GradBot, the official intelligent AI campus assistant for BVICAM \
                (Bharati Vidyapeeth's Institute of Computer Applications and Management, New Delhi). \
                Answer questions accurately, politely, and concisely for students, alumni, and faculty. \
                Use clear Markdown formatting with bullet points where appropriate."""
                + (liveContext.length() > 0 ? "\n" + liveContext : "");

        String answer = ollamaClientService.generateChatResponse(systemPrompt, question);

        String chatModel = ollamaClientService.getChatModel() != null
                ? ollamaClientService.getChatModel()
                : "gemma4:e4b";

        return Map.of(
                "answer", answer,
                "model", chatModel
        );
    }
}
